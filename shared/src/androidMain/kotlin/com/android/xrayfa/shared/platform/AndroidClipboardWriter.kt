package com.android.xrayfa.shared.platform

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

class AndroidClipboardWriter(
    private val context: Context,
) : ClipboardWriter {
    override fun writeText(text: String) {
        val clipboard =
            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("text", text))
    }
}
