@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.android.xrayfa.cli

import com.android.xrayfa.cli.host.*
import com.android.xrayfa.common.utils.calculateBytesHash
import kotlinx.serialization.json.*
import platform.posix.*

internal suspend fun checkNode(id: Int, output: String, coreOption: String?, level: LogLevel): Int {
    val log = LogSession(id, level)
    var exitCode = ExitCode.EXECUTION
    var primaryFailureAt: Long? = null
    var coreExit: Int? = null
    var artifact: String? = null
    var configHash: String? = null
    var corePath: String? = null
    var coreHash: String? = null
    var coreVersion = ""
    var timeout = false
    var interrupted = false
    var reportReserved = false
    var reportPath: String? = null
    val parser = NodeParser()
    log.event("prepare", "START client=$VERSION logs=${log.path}", force = true)
    try {
        val node =
            storeLock { directory -> NodeStoreCodec.find(loadStore(directory), id) }
                ?: fail(ExitCode.NODE_MISSING, "Node ID does not exist")
        val prepared = prepareStoredNode(node, parser, log)
        val outbound = prepared.outbound
        log.event("prepare", "Node loaded: ${summary(prepared.record)}", force = true)
        val final = runtimeConfig(outbound, level)
        require(
            final
                .getValue("inbounds")
                .jsonArray
                .single()
                .jsonObject
                .getValue("protocol")
                .jsonPrimitive
                .content == "tun"
        )
        val text = json.encodeToString(JsonObject.serializer(), final) + "\n"
        val path = absolute(output)
        val rp = path.removeSuffix(".json") + ".validation.json"
        if (access(path, F_OK) == 0 || access(rp, F_OK) == 0)
            fail(ExitCode.CONFLICT, "Output or validation report already exists")
        newFile(rp, "{}")
        reportReserved = true
        reportPath = rp
        newFile(path, text)
        artifact = path
        configHash = calculateBytesHash(text.encodeToByteArray())
        log.event("config", "Final JSON saved: $path sha256=$configHash", force = true)
        parser.adaptations.forEach { log.event("adapt", it, force = true) }
        corePath = canonicalCore(locateCore(callerIdentity(), coreOption))
        if (access(corePath, X_OK) != 0) fail(ExitCode.PERMISSION, "Core is not executable")
        coreHash = hashFile(corePath)
        log.event("prepare", "Core path=$corePath sha256=$coreHash", force = true)
        val version = coreProcess(corePath, listOf("version"), log, 5000)
        if (version.exit != 0 || version.timedOut || version.interrupted) {
            primaryFailureAt = version.observedAtMillis
            fail(
                if (version.timedOut || version.interrupted) ExitCode.EXECUTION
                else ExitCode.DEPENDENCY,
                "Core version probe failed",
            )
        }
        coreVersion = version.output.trim()
        log.event("prepare", "Core version=$coreVersion", force = true)
        val result = coreProcess(corePath, listOf("run", "-test", "-c", path), log)
        coreExit = result.exit
        timeout = result.timedOut
        interrupted = result.interrupted
        if (coreExit != 0 || timeout || interrupted) primaryFailureAt = result.observedAtMillis
        if (calculateBytesHash(readBytes(path)!!) != configHash)
            fail(ExitCode.CONFIGURATION, "Final JSON changed during validation")
        if (hashFile(corePath) != coreHash)
            fail(ExitCode.DEPENDENCY, "Core identity changed during validation")
        exitCode =
            when {
                timeout || interrupted -> ExitCode.EXECUTION
                coreExit != 0 -> ExitCode.CONFIGURATION
                else -> ExitCode.SUCCESS
            }
        log.event(
            "validate",
            if (exitCode == ExitCode.SUCCESS) "Config validation passed; core_exit=0"
            else
                "Config validation failed; core_exit=$coreExit timeout=$timeout interrupted=$interrupted",
            severity = if (exitCode == ExitCode.SUCCESS) LogLevel.INFO else LogLevel.ERROR,
            force = true,
        )
    } catch (e: CliError) {
        if (primaryFailureAt == null) primaryFailureAt = xf_millis()
        exitCode = e.exitCode
        log.event("error", e.message, severity = LogLevel.ERROR, force = true)
    } catch (_: Exception) {
        if (primaryFailureAt == null) primaryFailureAt = xf_millis()
        exitCode = ExitCode.CONFIGURATION
        log.event(
            "error",
            "Configuration generation/validation failed",
            severity = LogLevel.ERROR,
            force = true,
        )
    } finally {
        exitCode = exitAfterLogging(exitCode, primaryFailureAt, log.failedAtMillis)
        log.event(
            "finish",
            "END client=$VERSION exit=${exitCode.value} core_exit=${coreExit ?: "not-executed"}",
            severity = if (exitCode == ExitCode.SUCCESS) LogLevel.INFO else LogLevel.ERROR,
            force = true,
        )
        log.close()
        val finalExit = exitAfterLogging(exitCode, primaryFailureAt, log.failedAtMillis)
        if (finalExit == ExitCode.LOG && exitCode != ExitCode.LOG)
            fputs("Log persistence failed; final check exit=11 (first observed failure)\n", stderr)
        exitCode = finalExit
        if (reportReserved) {
            val report =
                ValidationReport(
                        clientVersion = VERSION,
                        nodeId = id,
                        utc = utc(),
                        sessionId = log.session,
                        configPath = artifact,
                        configSha256 = configHash,
                        corePath = corePath,
                        coreVersion = coreVersion,
                        coreSha256 = coreHash,
                        coreExitCode = coreExit,
                        exitCode = exitCode,
                        timedOut = timeout,
                        interrupted = interrupted,
                        logPath = log.path,
                        adaptations = parser.adaptations.toList(),
                    )
                    .encode()
            try {
                writeValidationReport(reportPath!!, report)
            } catch (_: Exception) {
                if (exitCode == ExitCode.SUCCESS) exitCode = ExitCode.IO
                fputs("Validation report write failed\n", stderr)
            }
        }
    }
    return exitCode.value
}

private fun writeValidationReport(path: String, report: JsonObject) {
    val fd = open(path, O_WRONLY or O_TRUNC or O_NOFOLLOW or O_CLOEXEC)
    if (fd < 0) fail(ExitCode.IO, "Cannot write validation report")
    try {
        writeFd(
            fd,
            (json.encodeToString(JsonObject.serializer(), report) + "\n").encodeToByteArray(),
        )
        if (fsync(fd) != 0) fail(ExitCode.IO, "Report sync failed")
    } finally {
        close(fd)
    }
}
