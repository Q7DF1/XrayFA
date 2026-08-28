package com.android.xrayfa.shared.platform.qr

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image
import qrcode.QRCode

/** Host-app QR bitmap for iOS [com.android.xrayfa.shared.ui.platform.IosPlatformRootHooks] ShareNode. */
fun encodeQrImageBitmap(contents: String): ImageBitmap? {
    if (contents.isEmpty()) return null
    return runCatching {
        val png = QRCode.ofSquares()
            .build(contents)
            .render()
            .getBytes()
        if (png.isEmpty()) return null
        Image.makeFromEncoded(png).toComposeImageBitmap()
    }.getOrNull()
}
