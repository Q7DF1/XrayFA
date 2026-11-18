@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.android.xrayfa.cli

import com.android.xrayfa.cli.host.xf_errno
import com.android.xrayfa.cli.host.xf_interrupted
import com.android.xrayfa.cli.host.xf_runtime_core
import com.android.xrayfa.cli.host.xf_socks_port_available
import com.android.xrayfa.cli.host.xf_try_lock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import platform.posix.ENOENT
import platform.posix.O_CLOEXEC
import platform.posix.O_CREAT
import platform.posix.O_NOFOLLOW
import platform.posix.O_RDONLY
import platform.posix.O_RDWR
import platform.posix.X_OK
import platform.posix.access
import platform.posix.chmod
import platform.posix.close
import platform.posix.getuid
import platform.posix.open
import platform.posix.unlink
import platform.posix.usleep

/** Foreground supervisor: no privileged network helpers or persistent process IDs. */
internal suspend fun runSocksNode(id: Int, coreOption: String?, level: LogLevel): Int {
    val log = LogSession(id, level)
    var core: RunningCore? = null
    var lockFd = -1
    var fd = -1
    var configPath: String? = null
    var exit = ExitCode.SUCCESS
    try {
        val record = storeLock { directory -> NodeStoreCodec.find(loadStore(directory), id) }
            ?: fail(ExitCode.NODE_MISSING, "Node ID does not exist")
        val prepared = prepareStoredNode(record, NodeParser(), log)
        val config = nodeSocksConfig(prepared.record, prepared.outbound, level)
        log.secrets = log.secrets + secretValues(config)
        val path = canonicalCore(locateCore(callerIdentity(), coreOption))
        if (access(path, X_OK) != 0) fail(ExitCode.PERMISSION, "Core is not executable")
        fd = open(path, O_RDONLY or O_NOFOLLOW or O_CLOEXEC)
        if (fd < 0) fail(ExitCode.DEPENDENCY, "Cannot open core")
        if (xf_runtime_core(fd, getuid(), 1) == 0)
            fail(ExitCode.PERMISSION, "Core must be a regular file owned by you or root, without group/other write permission")
        val coreHash = hashFile(path)
        privateDir(dataDir)
        val lockPath = "$dataDir/socks.lock"
        lockFd = open(lockPath, O_RDWR or O_CREAT or O_NOFOLLOW or O_CLOEXEC, 384)
        if (lockFd < 0) fail(ExitCode.IO, "Cannot open SOCKS5 session lock")
        ownedFile(lockPath)
        if (xf_try_lock(lockFd) != 0) fail(ExitCode.CONFLICT, "A SOCKS5 session is already running for this profile")
        if (xf_socks_port_available(SOCKS_PORT) == 0) fail(ExitCode.CONFLICT, "SOCKS5 port $SOCKS_PORT is unavailable")
        val output = "$dataDir/socks-${log.session}.json"
        newFile(output, json.encodeToString(JsonObject.serializer(), config) + "\n")
        configPath = output
        if (chmod(output, 256u) != 0) fail(ExitCode.IO, "Cannot freeze private configuration")
        val configHash = hashFile(output)
        val test = coreProcess(path, listOf("run", "-test", "-c", output), log,
            gate = ProcessGate(fd) {})
        if (test.timedOut || test.interrupted) fail(ExitCode.EXECUTION, "Core validation interrupted or timed out")
        if (test.exit != 0) fail(ExitCode.CONFIGURATION, "Core rejected SOCKS5 configuration")
        if (hashFile(output) != configHash || hashFile(path) != coreHash)
            fail(ExitCode.CONFLICT, "Core or configuration changed during validation")
        if (!log.flushBoundary()) fail(ExitCode.LOG, "Cannot persist runtime log")
        log.event("runtime", "START SOCKS5 listen=127.0.0.1:$SOCKS_PORT", force = true)
        core = RunningCore.gated(fd, path, listOf("run", "-c", output), log, dieWithParent = true)
        core.release()
        while (xf_interrupted() == 0) {
            core.tick()
            if (core.exitCode != null) fail(ExitCode.EXECUTION, "Proxy core exited")
            usleep(10000u)
        }
    } catch (error: CliError) {
        exit = error.exitCode
        log.event("runtime", error.message, severity = LogLevel.ERROR, force = true)
    } catch (_: Exception) {
        exit = ExitCode.EXECUTION
        log.event("runtime", "Proxy supervision failed", severity = LogLevel.ERROR, force = true)
    } finally {
        try {
            if (core?.stop() == false) exit = ExitCode.EXECUTION
        } catch (_: Exception) {
            exit = ExitCode.EXECUTION
        } finally {
            if (fd >= 0) close(fd)
            configPath?.let {
                if (unlink(it) != 0 && xf_errno() != ENOENT && exit == ExitCode.SUCCESS) exit = ExitCode.IO
            }
            if (lockFd >= 0) close(lockFd)
            log.event("runtime", "END SOCKS5 session exit=${exit.value}", force = true)
            log.close()
        }
    }
    if (exit == ExitCode.SUCCESS && log.failedAtMillis != null) exit = ExitCode.LOG
    return exit.value
}

internal fun socksDoctor(coreOption: String?, asJson: Boolean): Int {
    val path = canonicalCore(locateCore(callerIdentity(), coreOption))
    val result = coreProcess(path, listOf("version"), null, 5000)
    val ready = result.exit == 0 && !result.timedOut && !result.interrupted
    val report = buildJsonObject {
        put("schemaVersion", 1)
        put("operation", "doctor")
        put("mode", "socks5")
        put("readOnly", true)
        put("listen", "127.0.0.1")
        put("port", SOCKS_PORT)
        put("corePath", path)
        put("coreSha256", hashFile(path))
        put("readyForRun", ready)
        put("runtimeCertified", false)
    }
    if (asJson) println(json.encodeToString(JsonObject.serializer(), report))
    else println("SOCKS5 127.0.0.1:$SOCKS_PORT coreAvailable=$ready (bind and remote connectivity verified during use)")
    return if (ready) 0 else ExitCode.DEPENDENCY.value
}
