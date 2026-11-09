package com.android.xrayfa.cli

import kotlinx.serialization.json.*

/** Schema-1 report fields stay stable; report creation has no file or process side effects. */
internal data class ValidationReport(
    val clientVersion: String,
    val nodeId: Int,
    val utc: String,
    val sessionId: String,
    val configPath: String?,
    val configSha256: String?,
    val corePath: String?,
    val coreVersion: String,
    val coreSha256: String?,
    val coreExitCode: Int?,
    val exitCode: ExitCode,
    val timedOut: Boolean,
    val interrupted: Boolean,
    val logPath: String,
    val adaptations: List<String>,
) {
    fun encode(): JsonObject = buildJsonObject {
        put("schemaVersion", 1)
        put("clientVersion", clientVersion)
        put("nodeId", nodeId)
        put("utc", utc)
        put("sessionId", sessionId)
        put("configPath", configPath?.let(::JsonPrimitive) ?: JsonNull)
        put("configSha256", configSha256?.let(::JsonPrimitive) ?: JsonNull)
        put("corePath", corePath?.let(::JsonPrimitive) ?: JsonNull)
        put("coreVersion", coreVersion)
        put("coreSha256", coreSha256?.let(::JsonPrimitive) ?: JsonNull)
        put("coreExitCode", coreExitCode?.let(::JsonPrimitive) ?: JsonNull)
        put("exitCode", exitCode.value)
        put("success", exitCode == ExitCode.SUCCESS)
        put("timedOut", timedOut)
        put("interrupted", interrupted)
        put("logPath", logPath)
        put("adaptations", JsonArray(adaptations.map(::JsonPrimitive)))
    }
}

internal fun exitAfterLogging(
    current: ExitCode,
    primaryFailureAt: Long?,
    logFailureAt: Long?,
    preserveRecovery: Boolean = false,
): ExitCode =
    when {
        preserveRecovery && current == ExitCode.RECOVERY -> current
        logFailureAt != null && (primaryFailureAt == null || logFailureAt < primaryFailureAt) ->
            ExitCode.LOG
        else -> current
    }
