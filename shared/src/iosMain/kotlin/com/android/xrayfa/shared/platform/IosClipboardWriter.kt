package com.android.xrayfa.shared.platform

import platform.UIKit.UIPasteboard

class IosClipboardWriter : ClipboardWriter {
    override fun writeText(text: String) {
        UIPasteboard.generalPasteboard.string = text
    }
}
