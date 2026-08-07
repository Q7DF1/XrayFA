package com.android.xrayfa.shared.platform

import android.content.ClipboardManager
import android.content.Context

class AndroidClipboardReader(
    private val context: Context,
) : ClipboardReader {
    override fun readText(): String {
        val clipboard =
            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = clipboard.primaryClip
        return if (clipData != null && clipData.itemCount > 0) {
            clipData.getItemAt(0).coerceToText(context).toString()
        } else {
            ""
        }
    }
}
