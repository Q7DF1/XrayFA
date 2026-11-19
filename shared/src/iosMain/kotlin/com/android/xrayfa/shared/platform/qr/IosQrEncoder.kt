package com.android.xrayfa.shared.platform.qr

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image
import qrcode.QRCode
import qrcode.color.Colors

/** Host-app QR bitmap for iOS [com.android.xrayfa.shared.ui.platform.IosPlatformRootHooks] ShareNode. */
fun encodeQrImageBitmap(contents: String): ImageBitmap? {
    if (contents.isEmpty()) return null
    return runCatching {
        // Four modules are the entire quiet zone; no fixed display padding.
        val modulePixels = 8
        val png = QRCode.ofSquares()
            .withSize(modulePixels)
            .withMargin(4 * modulePixels)
            .withBackgroundColor(Colors.WHITE)
            .build(contents)
            .render()
            .getBytes()
        if (png.isEmpty()) return null
        Image.makeFromEncoded(png).toComposeImageBitmap()
    }.getOrNull()
}
