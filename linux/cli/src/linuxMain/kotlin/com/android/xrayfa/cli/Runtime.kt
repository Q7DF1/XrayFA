@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.android.xrayfa.cli

import com.android.xrayfa.cli.host.*
import kotlinx.cinterop.*
import kotlinx.serialization.json.*
import platform.posix.*

internal data class CallerIdentity(val uid: UInt, val home: String, val sudo: Boolean) {
    val paths
        get() = callerPaths(uid, home)

    val profile
        get() = selectedStoreDirectory(paths)

    val systemLogs
        get() = paths.systemLogDir
}

internal fun callerPaths(uid: UInt, ownerHome: String): CliPaths =
    CliPaths.resolve(
        passwdHome = ownerHome,
        uid = uid,
        isolationRoot =
            getenv("XRAYFA_HOME")?.toKString()?.takeIf { it.isNotBlank() }?.let(::absolute),
        standardParentUsable = xf_profile_parent_usable(ownerHome, uid) != 0,
    )

internal fun selectedStoreDirectory(paths: CliPaths): String =
    try {
        paths.storeDirectory { directory ->
            when (xf_store_presence(directory, paths.uid)) {
                0 -> StorePresence.MISSING
                1 -> StorePresence.PRESENT
                else -> StorePresence.UNSAFE
            }
        }
    } catch (_: IllegalArgumentException) {
        fail(ExitCode.IO, "Conflicting or unsafe node stores; originals preserved")
    }

internal fun locateCore(caller: CallerIdentity, explicit: String?): String =
    CoreLocator.locate(caller.home, explicit) { access(it, F_OK) == 0 }

internal fun callerIdentity(): CallerIdentity {
    val effective = geteuid()
    val sudoUid = if (effective == 0u) getenv("SUDO_UID")?.toKString() else null
    val uid =
        if (sudoUid != null) {
            if (!Regex("[0-9]+").matches(sudoUid)) fail(ExitCode.PERMISSION, "Invalid SUDO_UID")
            sudoUid.toUIntOrNull() ?: fail(ExitCode.PERMISSION, "Invalid SUDO_UID")
        } else getuid()
    val path =
        xf_uid_home(uid)?.toKString() ?: fail(ExitCode.PERMISSION, "Caller UID has no passwd home")
    if (!path.startsWith('/')) fail(ExitCode.PERMISSION, "Caller passwd home is not absolute")
    return CallerIdentity(uid, path, sudoUid != null)
}

internal fun tool(
    path: String,
    args: List<String>,
    timeout: Long = 5000,
    maxLineLength: Int = 16384,
    maxCapturedBytes: Int = 65536,
    context: OperationContext = OperationContext(),
): ProcessResult {
    return coreProcess(
        path,
        args,
        null,
        context.timeout(xf_millis(), timeout),
        ignoreSignals = true,
        maxLineLength = maxLineLength,
        maxCapturedBytes = maxCapturedBytes,
    )
}

private fun executablePath(vararg candidates: String): String? =
    candidates.firstOrNull { access(it, X_OK) == 0 }

/** Read-only capability report. Required failures are actionable and return non-zero. */
internal fun doctor(coreOption: String?, asJson: Boolean): Int {
    val caller = callerIdentity()
    val checks = mutableListOf<JsonObject>()
    fun probe(name: String, path: String?, args: List<String>, required: Boolean): JsonObject {
        var failure: ExitCode? = null
        val result =
            try {
                if (path == null) null else tool(path, args)
            } catch (e: CliError) {
                failure = e.exitCode
                null
            } catch (_: Exception) {
                null
            }
        val status =
            if (failure == ExitCode.PERMISSION) ProbeStatus.PERMISSION_DENIED
            else classifyProbe(path, result)
        return buildJsonObject {
            put("name", name)
            put("required", required)
            put("status", status.wire)
            put("path", path?.let(::JsonPrimitive) ?: JsonNull)
            put("exit", result?.exit?.let(::JsonPrimitive) ?: JsonNull)
            put(
                "reason",
                if (status == ProbeStatus.OBSERVED) "Probe completed"
                else "Probe did not establish the required capability",
            )
            put("output", result?.output ?: "Required executable unavailable or probe failed")
            put("timedOut", result?.timedOut ?: false)
        }
    }
    val cat = executablePath("/bin/cat", "/usr/bin/cat")
    val uname = executablePath("/bin/uname", "/usr/bin/uname")
    val ip = executablePath("/sbin/ip", "/usr/sbin/ip")
    val nft = executablePath("/usr/sbin/nft", "/sbin/nft")
    var corePath: String? = null
    var coreHash: String? = null
    var coreVersion: String? = null
    var coreCheck = buildJsonObject {
        put("name", "core")
        put("required", true)
        put("status", "missing")
        put("path", JsonNull)
        put("reason", "Core path does not exist or is not executable")
        put("output", "")
    }
    try {
        corePath = canonicalCore(locateCore(caller, coreOption))
        coreHash = hashFile(corePath)
        val version = tool(corePath, listOf("version"))
        val ok = version.exit == 0 && !version.timedOut
        coreVersion = if (ok) version.output.trim() else "probe-failed"
        coreCheck = buildJsonObject {
            put("name", "core")
            put("required", true)
            put("status", if (ok) "observed" else if (version.timedOut) "timeout" else "failed")
            put("path", corePath)
            put("exit", version.exit)
            put("reason", if (ok) "Core version probe completed" else "Core version probe failed")
            put("output", version.output)
            put("timedOut", version.timedOut)
        }
    } catch (_: CliError) {
        coreCheck = buildJsonObject {
            put("name", "core")
            put("required", true)
            put("status", "failed")
            put("path", corePath?.let(::JsonPrimitive) ?: JsonNull)
            put("reason", "Core identity or version probe failed")
            put("output", "Core path, hash, or version could not be verified")
        }
    } catch (_: Exception) {
        coreCheck = buildJsonObject {
            put("name", "core")
            put("required", true)
            put("status", "failed")
            put("path", corePath?.let(::JsonPrimitive) ?: JsonNull)
            put("reason", "Core identity or version probe raised a runtime error")
            put("output", "Core path, hash, or version could not be verified")
        }
    }
    checks.add(coreCheck)
    checks.add(probe("distribution", cat, listOf("/etc/os-release"), true))
    checks.add(probe("capabilities", cat, listOf("/proc/self/status"), true))
    checks.add(probe("kernel", uname, listOf("-a"), true))
    checks.add(probe("links", ip, listOf("address"), true))
    checks.add(probe("routes", ip, listOf("route", "show", "table", "all"), true))
    checks.add(probe("rules", ip, listOf("rule"), true))
    val resolverCheck =
        try {
            val snapshot = resolverSnapshot()
            buildJsonObject {
                put("name", "resolver")
                put("required", true)
                put("status", "observed")
                put("path", snapshot.getValue("backend"))
                put("exit", 0)
                put("reason", "Resolver backend observed and /etc/resolv.conf is readable")
                put("output", snapshot.getValue("backend"))
                put("timedOut", false)
            }
        } catch (e: Exception) {
            buildJsonObject {
                put("name", "resolver")
                put("required", true)
                put("status", if (e is CliError) "failed" else "unknown")
                put("path", JsonPrimitive(RESOLV_CONF))
                put("exit", JsonNull)
                put("reason", if (e is CliError) e.message else "Resolver probe failed")
                put(
                    "output",
                    "Resolver backend could not be observed without exposing resolver contents",
                )
                put("timedOut", false)
            }
        }
    checks.add(resolverCheck)
    checks.add(probe("nft", nft, listOf("list", "ruleset"), true))
    var ipv6Capability = buildJsonObject {
        put("status", "unknown")
        put("policy", "block-non-loopback")
        put("globalDefaultRoute", false)
    }
    val ipv6Check =
        try {
            val snapshot = ipv6CapabilitySnapshot()
            ipv6Capability = snapshot
            buildJsonObject {
                put("name", "ipv6")
                put("required", false)
                put("status", "observed")
                put("path", "ip -6 route")
                put("exit", 0)
                put(
                    "reason",
                    if (snapshot.getValue("globalDefaultRoute").jsonPrimitive.boolean)
                        "Global IPv6 default route observed"
                    else "No global IPv6 default route observed; runtime remains IPv4-only",
                )
                put("output", json.encodeToString(JsonObject.serializer(), snapshot))
                put("timedOut", false)
            }
        } catch (e: Exception) {
            buildJsonObject {
                put("name", "ipv6")
                put("required", false)
                put("status", "unknown")
                put("path", "ip -6 route")
                put("exit", JsonNull)
                put(
                    "reason",
                    e.message?.takeIf { it.isNotBlank() } ?: "IPv6 capability probe failed",
                )
                put("output", "IPv6 capability could not be observed read-only")
                put("timedOut", false)
            }
        }
    checks.add(ipv6Check)
    var conflictSnapshot = buildJsonObject {
        put("status", "unknown")
        putJsonArray("items") {}
        put("networkNamespace", networkNamespace())
    }
    val conflicts =
        try {
            val snapshot = runtimeConflictSnapshot()
            conflictSnapshot = snapshot
            buildJsonObject {
                put("name", "conflicts")
                put("required", true)
                put(
                    "status",
                    if (snapshot.getValue("status").jsonPrimitive.content == "clear") "observed"
                    else snapshot.getValue("status").jsonPrimitive.content,
                )
                put("path", "network")
                put("exit", 0)
                put(
                    "reason",
                    if (snapshot.getValue("status").jsonPrimitive.content == "clear")
                        "No owned or foreign runtime conflicts observed"
                    else "Runtime resources require resolution before run",
                )
                put("output", json.encodeToString(JsonObject.serializer(), snapshot))
                put("timedOut", false)
            }
        } catch (e: Exception) {
            conflictSnapshot = buildJsonObject {
                put("status", "unknown")
                putJsonArray("items") {}
                put("networkNamespace", networkNamespace())
            }
            buildJsonObject {
                put("name", "conflicts")
                put("required", true)
                put("status", "unknown")
                put("path", "network")
                put("exit", JsonNull)
                put("reason", e.message?.takeIf { it.isNotBlank() } ?: "Conflict inventory failed")
                put("output", "Network conflict state could not be observed safely")
                put("timedOut", false)
            }
        }
    checks.add(conflicts)
    val tun = access("/dev/net/tun", R_OK or W_OK) == 0
    val blockers =
        checks
            .filter {
                it["required"]?.jsonPrimitive?.boolean == true &&
                    it["status"]?.jsonPrimitive?.content != ProbeStatus.OBSERVED.wire
            }
            .map {
                BlockingReason(
                    it.getValue("name").jsonPrimitive.content,
                    ProbeStatus.parse(it.getValue("status").jsonPrimitive.content),
                )
            }
            .toMutableList()
    if (!tun) blockers += BlockingReason("tun", ProbeStatus.MISSING)
    val ready = blockers.isEmpty()
    val report = buildJsonObject {
        put("schemaVersion", 2)
        put("operation", "doctor")
        put("readOnly", true)
        put("uid", caller.uid.toLong())
        put("effectiveUid", geteuid().toLong())
        put("sudo", caller.sudo)
        put("profile", caller.profile)
        put("systemLogPath", caller.systemLogs)
        put("recoveryPath", CliPaths.JOURNAL)
        put("corePath", corePath?.let(::JsonPrimitive) ?: JsonNull)
        put("coreSha256", coreHash?.let(::JsonPrimitive) ?: JsonNull)
        put("coreVersion", coreVersion?.let(::JsonPrimitive) ?: JsonNull)
        put("tunDevice", tun)
        put("readyForRun", ready)
        put("runtimeCertified", false)
        put("bootId", bootId())
        put("networkNamespace", networkNamespace())
        put("ipv6", ipv6Capability)
        put("conflicts", conflictSnapshot)
        putJsonArray("blockingReasons") { blockers.forEach { add(it.display()) } }
        put("checks", JsonArray(checks))
    }
    if (asJson) println(json.encodeToString(JsonObject.serializer(), report))
    else {
        println("XrayFA doctor (read-only)")
        println(
            "readyForRun=$ready UID=${caller.uid} effectiveUID=${geteuid()} profile=${caller.profile}"
        )
        println("Core=${corePath ?: "unavailable"} sha256=${coreHash ?: "unavailable"}")
        if (blockers.isNotEmpty())
            println("Blocking reasons: ${blockers.joinToString(", ") { it.display() }}")
        checks.forEach {
            println(
                "${it.getValue("name").jsonPrimitive.content}: ${it.getValue("status").jsonPrimitive.content} — ${it.getValue("reason").jsonPrimitive.content}"
            )
        }
    }
    return doctorExit(blockers).value
}

/** Text fallback belongs only to this external-tool adapter; errno failures retain typed codes. */
private fun classifyProbe(path: String?, result: ProcessResult?): ProbeStatus =
    when {
        path == null -> ProbeStatus.MISSING
        result == null -> ProbeStatus.UNKNOWN
        result.timedOut -> ProbeStatus.TIMEOUT
        result.exit == 0 -> ProbeStatus.OBSERVED
        result.output.contains("Permission denied", ignoreCase = true) ->
            ProbeStatus.PERMISSION_DENIED
        else -> ProbeStatus.FAILED
    }
