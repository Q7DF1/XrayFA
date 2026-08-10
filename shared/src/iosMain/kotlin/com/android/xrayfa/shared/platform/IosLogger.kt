package com.android.xrayfa.shared.platform

import com.android.xrayfa.common.utils.AppLogStore
import com.android.xrayfa.common.utils.Logger

/** iOS [Logger] until os_log wiring lands. */
class IosLogger : Logger {
    override fun i(tag: String, message: String) {
        AppLogStore.append(tag, "I", message)
        println("[$tag] $message")
    }

    override fun e(tag: String, message: String, throwable: Throwable?) {
        val detail =
            if (throwable != null) {
                "$message (${throwable.message.orEmpty()})"
            } else {
                message
            }
        AppLogStore.append(tag, "E", detail)
        println("[$tag] ERROR: $detail")
    }
}
