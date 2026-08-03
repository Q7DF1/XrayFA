package com.android.xrayfa.common.utils

/**
 * Platform logging for shared logic.
 *
 * Android actual delegates to Logcat; iOS will use NSLog / os_log when this
 * module becomes KMP.
 */
interface Logger {
    fun i(tag: String, message: String)
    fun e(tag: String, message: String, throwable: Throwable? = null)
}

internal object NoOpLogger : Logger {
    override fun i(tag: String, message: String) = Unit
    override fun e(tag: String, message: String, throwable: Throwable?) = Unit
}
