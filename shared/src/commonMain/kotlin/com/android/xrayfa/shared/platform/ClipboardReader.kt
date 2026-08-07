package com.android.xrayfa.shared.platform

/** Cross-platform clipboard text access for Config link import. */
interface ClipboardReader {
    fun readText(): String
}
