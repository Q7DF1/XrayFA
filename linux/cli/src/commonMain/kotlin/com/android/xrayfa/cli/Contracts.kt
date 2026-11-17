package com.android.xrayfa.cli

/** Public exit numbers are stable, including for shell scripts and validation reports. */
internal enum class ExitCode(val value: Int) {
    SUCCESS(0),
    USAGE(2),
    NODE_MISSING(3),
    PERMISSION(4),
    DEPENDENCY(5),
    CONFIGURATION(6),
    EXECUTION(7),
    CONFLICT(8),
    RECOVERY(9),
    IO(10),
    LOG(11),
}

internal class CliError(val exitCode: ExitCode, override val message: String) : Exception(message) {
    val code
        get() = exitCode.value
}

internal fun fail(code: ExitCode, message: String): Nothing = throw CliError(code, message)

internal enum class LogLevel(val wire: String, val priority: Int) {
    DEBUG("debug", 0),
    INFO("info", 1),
    WARNING("warning", 2),
    ERROR("error", 3);

    fun accepts(severity: LogLevel) = severity.priority >= priority

    companion object {
        fun parse(value: String): LogLevel =
            entries.firstOrNull { it.wire == value } ?: fail(ExitCode.USAGE, "Invalid log level")
    }
}

internal enum class ProbeStatus(val wire: String) {
    MISSING("missing"),
    UNKNOWN("unknown"),
    TIMEOUT("timeout"),
    OBSERVED("observed"),
    PERMISSION_DENIED("permission-denied"),
    FAILED("failed"),
    CONFLICT("conflict");

    companion object {
        fun parse(value: String): ProbeStatus = entries.firstOrNull { it.wire == value } ?: UNKNOWN
    }
}

internal data class BlockingReason(val name: String, val status: ProbeStatus) {
    fun display() = "$name: ${status.wire}"
}

internal fun doctorExit(blockers: List<BlockingReason>): ExitCode =
    when {
        blockers.isEmpty() -> ExitCode.SUCCESS
        blockers.any { it.status == ProbeStatus.PERMISSION_DENIED } -> ExitCode.PERMISSION
        else -> ExitCode.DEPENDENCY
    }
