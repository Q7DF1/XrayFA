@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.android.xrayfa.cli

import com.android.xrayfa.cli.host.*
import com.android.xrayfa.common.utils.calculateBytesHash
import kotlinx.cinterop.*
import kotlinx.serialization.json.*
import platform.posix.*

internal class RuntimeStop : Exception()

internal fun runNode(id: Int, coreOption: String?, level: LogLevel): Int {
    if (geteuid() != 0u)
        fail(ExitCode.PERMISSION, "TUN run requires root; invoke explicitly with sudo")
    val caller = callerIdentity()
    val policy = TunPolicy.CURRENT
    try {
        privateDir("/var/log/xrayfa")
    } catch (_: Exception) {
        fail(ExitCode.LOG, "Cannot initialize system log directory")
    }
    val log = LogSession(id, level, caller.systemLogs, caller.uid, nonBlockingTerminal = true)
    var lease: NetworkLease? = null
    var core: RunningCore? = null
    var coreFd = -1
    var code = ExitCode.EXECUTION
    var primaryAt: Long? = null
    var phase = "prepare"
    try {
        log.event(
            "prepare",
            "START runtime uid=${caller.uid} profile=${caller.profile}",
            force = true,
        )
        val node = nodeSnapshot(caller, id)
        val parser = NodeParser()
        val prepared = prepareStoredNode(node, parser, log)
        val outbound = prepared.outbound
        log.event("prepare", "Node loaded: ${summary(prepared.record)}", force = true)
        val corePath = canonicalCore(locateCore(caller, coreOption))
        coreFd = open(corePath, O_RDONLY or O_NOFOLLOW or O_CLOEXEC)
        if (coreFd < 0) fail(ExitCode.DEPENDENCY, "Cannot open core")
        if (xf_runtime_core(coreFd, caller.uid, if (caller.sudo) 1 else 0) == 0)
            fail(
                ExitCode.PERMISSION,
                "Runtime core must be root-owned, or caller-owned when invoked via sudo, and not group/other writable",
            )
        if (access(corePath, X_OK) != 0) fail(ExitCode.PERMISSION, "Core is not executable")
        val pinned = "/proc/${getpid()}/fd/$coreFd"
        val coreHash = hashFile(pinned, true)
        phase = "core-version"
        val version = coreProcess(pinned, listOf("version"), log, 5000)
        if (version.exit != 0 || version.timedOut || version.interrupted)
            fail(ExitCode.DEPENDENCY, "Core version probe failed")
        log.event(
            "prepare",
            "Core path=$corePath sha256=$coreHash version=${version.output.trim()}",
            force = true,
        )
        phase = "network-preflight"
        val plan = NetworkPlan.inspect(log.session)
        phase = "config-generation"
        val config = runtimeFinalConfig(bootstrapOutbound(outbound, log), level, plan)
        privateDir("/run/xrayfa")
        privateDir("/run/xrayfa/artifacts")
        val dir = "/run/xrayfa/artifacts/${log.session}"
        privateDir(dir)
        val path = "$dir/final.json"
        val text = json.encodeToString(JsonObject.serializer(), config) + "\n"
        newFile(path, text)
        if (chmod(path, 256u) != 0) fail(ExitCode.IO, "Cannot freeze final config permissions")
        val configHash = calculateBytesHash(text.encodeToByteArray())
        log.event("validate", "Final JSON=$path sha256=$configHash", force = true)
        val test = coreProcess(pinned, listOf("run", "-test", "-c", path), log)
        if (test.timedOut || test.interrupted)
            fail(ExitCode.EXECUTION, "Final configuration validation interrupted or timed out")
        if (test.exit != 0) fail(ExitCode.CONFIGURATION, "Final configuration validation failed")
        if (calculateBytesHash(readBytes(path)!!) != configHash)
            fail(ExitCode.CONFIGURATION, "Final JSON changed during validation")
        if (hashFile(pinned, true) != coreHash)
            fail(ExitCode.DEPENDENCY, "Core changed during validation")
        if (!log.flushBoundary())
            fail(ExitCode.LOG, "Runtime log persistence failed before network changes")
        phase = "lease-and-network-setup"
        lease = NetworkLease.acquire(caller, id, configHash, coreHash)
        lease.update(
            JournalField.BASELINE to plan.baseline,
            JournalField.CONFIG_PATH to JsonPrimitive(path),
            JournalField.CORE_PATH to JsonPrimitive(corePath),
            JournalField.CORE_DEVICE to JsonPrimitive(xf_fd_device(coreFd).toString()),
            JournalField.CORE_INODE to JsonPrimitive(xf_fd_inode(coreFd).toString()),
            JournalField.UNDERLAY to JsonPrimitive(plan.underlay),
        )
        if (normalizedNetwork(networkBaseline()) != normalizedNetwork(plan.baseline))
            fail(
                ExitCode.CONFLICT,
                "Network changed during validation; retry after resolving the conflict",
            )
        ensureManagementRoutes(plan, lease, log)
        applyIpv6(plan, lease)
        lease.advance(SessionPhase.CORE_INTENT)
        phase = "core-start"
        core = RunningCore.gated(coreFd, corePath, listOf("run", "-c", path), log)
        lease.advance(
            SessionPhase.CORE_GATED,
            JournalField.CORE_PID to JsonPrimitive(core.pid),
            JournalField.CORE_START to
                JsonPrimitive(
                    procStart(core.pid) ?: fail(ExitCode.EXECUTION, "Cannot identify gated core")
                ),
        )
        core.release()
        phase = "tun-readiness"
        val tun = awaitTunReadiness(core, lease, log, policy)
        verifyVirtualDnsPath()
        phase = "resolver-setup"
        configureSessionResolver(lease, policy)
        phase = "running"
        log.event(
            "Running",
            "Local resources ready; remote request success is measured separately; underlay=${plan.underlay}",
            force = true,
        )
        superviseSession(core, lease, log, plan, tun)
        code = ExitCode.SUCCESS
        log.event("exit", "Signal ${xf_interrupted()} requested stop", force = true)
    } catch (_: RuntimeStop) {
        code = ExitCode.SUCCESS
    } catch (e: CliError) {
        primaryAt = xf_millis()
        code = e.exitCode
        log.event("error", e.message, severity = LogLevel.ERROR, force = true)
    } catch (e: Exception) {
        primaryAt = xf_millis()
        code = ExitCode.IO
        val type = e::class.simpleName ?: "${e::class}"
        val detail = e.message?.trim()?.takeIf { it.isNotEmpty() }?.let { ": ${clean(it)}" } ?: ""
        log.event(
            "error",
            "Runtime operation failed at $phase [$type]$detail",
            severity = LogLevel.ERROR,
            force = true,
        )
    } finally {
        if (lease != null) {
            val complete = restoreOwned(lease.record, log, core, lease)
            if (!complete) code = ExitCode.RECOVERY
            else
                try {
                    finishJournal(lease.record, log)
                } catch (_: Exception) {
                    code = ExitCode.RECOVERY
                }
            lease.close()
        } else if (core != null && !core.stop()) code = ExitCode.RECOVERY
        if (coreFd >= 0) close(coreFd)
        code = exitAfterLogging(code, primaryAt, log.failedAtMillis, preserveRecovery = true)
        log.event(
            "finish",
            "END runtime exit=${code.value} core_exit=${core?.exitCode ?: "not-executed"}",
            severity = if (code == ExitCode.SUCCESS) LogLevel.INFO else LogLevel.ERROR,
            force = true,
        )
        log.close()
        code = exitAfterLogging(code, primaryAt, log.failedAtMillis, preserveRecovery = true)
    }
    return code.value
}
