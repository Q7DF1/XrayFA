package com.android.xrayfa.cli

/** One explicit budget follows a recovery operation through observations and mutations. */
internal data class OperationContext(val deadlineMillis: Long? = null) {
    fun timeout(now: Long, requested: Long, reserve: Long = 1000): Long {
        val remaining = deadlineMillis?.let { it - now - reserve } ?: return requested
        if (remaining <= 0) fail(ExitCode.RECOVERY, "Overall recovery deadline reached")
        return minOf(requested, remaining)
    }
}
