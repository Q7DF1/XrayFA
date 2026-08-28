package com.android.xrayfa.shared.platform

/** Cross-platform clipboard write for sharing subscription URLs, etc. */
interface ClipboardWriter {
    fun writeText(text: String)
}
