package com.android.xrayfa.shared.platform

import com.android.xrayfa.common.utils.Logger

/** iOS [Logger] until os_log wiring lands. */
class IosLogger : Logger {
    override fun i(tag: String, message: String) {
        println("[$tag] $message")
    }

    override fun e(tag: String, message: String, throwable: Throwable?) {
        println("[$tag] ERROR: $message ${throwable?.message.orEmpty()}")
    }
}
