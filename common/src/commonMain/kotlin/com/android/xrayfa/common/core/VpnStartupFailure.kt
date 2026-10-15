package com.android.xrayfa.common.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet

enum class StartupFailureKind(val code: String) {
    NO_NODE("START-01"), INVALID_LINK("START-02"), INVALID_JSON("START-03"),
    UNSUPPORTED_FEATURE("START-04"), INVALID_PROTOCOL("START-05"), INVALID_SECURITY("START-06"),
    GEODATA_MISSING("START-07"), GEODATA_INVALID("START-08"), GEO_CATEGORY_MISSING("START-09"),
    INVALID_ROUTING("START-10"), INVALID_DNS("START-11"), PORT_IN_USE("START-12"),
    VPN_PERMISSION("START-13"), VPN_INTERFACE("START-14"), TUN_START("START-15"),
    FILE_ACCESS("START-16"), CORE_INITIALIZATION("START-17"), UNKNOWN("START-18"),
    NATIVE_LIBRARY_MISSING("START-19"), NATIVE_LIBRARY_INCOMPATIBLE("START-20"),
}

enum class StartupStage { PREPARATION, CONFIGURATION, GEODATA, VPN, CORE, TUN, APPLICATION }
enum class GeodataStatus { MISSING, INVALID, CATEGORY_MISSING, VALID }

data class VpnStartupFailure(
    val kind: StartupFailureKind,
    val stage: StartupStage,
    val technicalDetails: String,
    val subject: String? = null,
) {
    val canRestoreGeodata: Boolean get() = kind in setOf(
        StartupFailureKind.GEODATA_MISSING, StartupFailureKind.GEODATA_INVALID,
        StartupFailureKind.GEO_CATEGORY_MISSING,
    )
}

/** Redact before storing, displaying, logging or copying diagnostics. */
fun redactStartupDetails(text: String): String {
    var safe = text.replace(Regex("(?i)[a-z][a-z0-9+.-]*://[^\\s\"'<>]+"), "[REDACTED LINK]")
    safe = safe.replace(Regex("(?i)\\b[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}\\b"), "[REDACTED UUID]")
    safe = safe.replace(Regex(
        "(?i)([\"']?(?:password|pass|auth|token|uuid|id|privateKey|encryption|publicKey|pbk|shortId|sid)[\"']?\\s*[:=]\\s*)(?:\"[^\"]*\"|'[^']*'|[^,\\s;>]+)",
    )) { "${it.groupValues[1]}[REDACTED]" }
    return safe.filter { it == '\n' || it == '\t' || !it.isISOControl() }.take(4_000)
}

fun startupExceptionDetails(error: Throwable): String {
    val seen = mutableSetOf<Throwable>()
    val parts = mutableListOf<String>()
    var cause: Throwable? = error
    while (cause != null && seen.add(cause) && parts.size < 8) {
        parts += "${cause::class.simpleName ?: "Error"}: ${cause.message.orEmpty()}"
        cause = cause.cause
    }
    return parts.joinToString("\nCaused by: ")
}

fun classifyStartupFailure(
    error: Throwable,
    stage: StartupStage,
    geodataStatus: GeodataStatus? = null,
): VpnStartupFailure {
    val raw = startupExceptionDetails(error)
    val message = raw.lowercase()
    val geo = message.contains("geosite") || message.contains("geoip") || message.contains("geodata")
    val kind = when {
        "dlopen failed" in message && ("not found" in message || "no such file" in message) -> StartupFailureKind.NATIVE_LIBRARY_MISSING
        listOf("unsatisfiedlinkerror", "dlopen failed", "cannot locate symbol", "wrong elf", "no implementation found for").any { it in message } -> StartupFailureKind.NATIVE_LIBRARY_INCOMPATIBLE
        listOf("permission denied", "eacces", "read-only file", "no space left", "enospc").any { it in message } -> StartupFailureKind.FILE_ACCESS
        geo && geodataStatus == GeodataStatus.MISSING -> StartupFailureKind.GEODATA_MISSING
        geo && geodataStatus == GeodataStatus.CATEGORY_MISSING -> StartupFailureKind.GEO_CATEGORY_MISSING
        geo && (geodataStatus == GeodataStatus.INVALID || (geodataStatus != GeodataStatus.VALID && ("incomplete" in message || "invalid" in message))) -> StartupFailureKind.GEODATA_INVALID
        geo && ("not found" in message || "no such file" in message || "failed to open" in message) -> StartupFailureKind.GEODATA_MISSING
        geo && "eof" in message && geodataStatus != GeodataStatus.VALID -> StartupFailureKind.GEODATA_INVALID
        listOf("address already in use", "eaddrinuse", "bind: address").any { it in message } -> StartupFailureKind.PORT_IN_USE
        "vpn_permission" in message -> StartupFailureKind.VPN_PERMISSION
        "vpn_interface" in message -> StartupFailureKind.VPN_INTERFACE
        "tun_start" in message || stage == StartupStage.TUN -> StartupFailureKind.TUN_START
        "no_node" in message -> StartupFailureKind.NO_NODE
        "jsonconfigexception" in message || "failed to parse json" in message || "invalid json" in message || "json decoding" in message || "json_config_error" in message -> StartupFailureKind.INVALID_JSON
        listOf("no longer supports", "removed feature", "has been removed", "is removed", "unsupported transport", "unsupported protocol", "unsupported security").any { it in message } -> StartupFailureKind.UNSUPPORTED_FEATURE
        listOf("reality", "fingerprint", "shortid", "short id", "public key", "certificate", "pinnedpeercert", "verifypeercert").any { it in message } -> StartupFailureKind.INVALID_SECURITY
        "share link" in message || "malformed escape" in message || "invalid uri" in message -> StartupFailureKind.INVALID_LINK
        listOf("vless users", "vmess id", "encryption", "unsupported flow", "unsupported xhttp mode", "xhttp extra", "obfs", "invalid port").any { it in message } -> StartupFailureKind.INVALID_PROTOCOL
        "dns" in message && ("invalid" in message || "failed" in message || "unknown" in message) -> StartupFailureKind.INVALID_DNS
        !geo && ("routing" in message || "invalid field rule" in message || "outboundtag" in message || "balancertag" in message) -> StartupFailureKind.INVALID_ROUTING
        stage == StartupStage.GEODATA -> StartupFailureKind.GEODATA_INVALID
        stage == StartupStage.VPN -> if ("securityexception" in message) StartupFailureKind.VPN_PERMISSION else StartupFailureKind.VPN_INTERFACE
        stage == StartupStage.CORE && !geo -> StartupFailureKind.CORE_INITIALIZATION
        else -> StartupFailureKind.UNKNOWN
    }
    val subject = Regex("(?i)failed to (?:check|load) code\\s+([a-z0-9_.@-]+)\\s+from\\s+(geo(?:site|ip)\\.dat)")
        .find(raw)?.let { "${it.groupValues[2]}: ${it.groupValues[1].uppercase()}" }
    val resolvedStage = when (kind) {
        StartupFailureKind.GEODATA_MISSING, StartupFailureKind.GEODATA_INVALID, StartupFailureKind.GEO_CATEGORY_MISSING -> StartupStage.GEODATA
        StartupFailureKind.INVALID_JSON, StartupFailureKind.INVALID_LINK, StartupFailureKind.INVALID_PROTOCOL,
        StartupFailureKind.INVALID_SECURITY, StartupFailureKind.INVALID_ROUTING, StartupFailureKind.INVALID_DNS,
        StartupFailureKind.UNSUPPORTED_FEATURE -> StartupStage.CONFIGURATION
        StartupFailureKind.NATIVE_LIBRARY_MISSING, StartupFailureKind.NATIVE_LIBRARY_INCOMPATIBLE -> StartupStage.APPLICATION
        else -> stage
    }
    return VpnStartupFailure(kind, resolvedStage, redactStartupDetails(raw), subject)
}

data class VpnStartupState(val attemptId: Long = 0, val pending: Boolean = false, val failure: VpnStartupFailure? = null)

/** Shared by the Android service, manager and UI; rejects results from superseded attempts. */
class VpnStartupStateStore {
    private val mutableState = MutableStateFlow(VpnStartupState())
    val state = mutableState.asStateFlow()

    fun begin(): Long = mutableState.updateAndGet { it.copy(attemptId = it.attemptId + 1, pending = true) }.attemptId
    fun fail(failure: VpnStartupFailure, attemptId: Long = state.value.attemptId) {
        mutableState.update { if (it.attemptId == attemptId) it.copy(pending = false, failure = failure) else it }
    }
    fun succeed(attemptId: Long) {
        mutableState.update { if (it.attemptId == attemptId && it.pending) it.copy(pending = false, failure = null) else it }
    }
}
