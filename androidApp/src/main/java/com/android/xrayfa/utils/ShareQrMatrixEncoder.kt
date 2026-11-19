package com.android.xrayfa.utils

import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix

/** Keep exactly four quiet-zone modules, without fixed-canvas rounding padding. */
internal object ShareQrMatrixEncoder {
    fun encode(contents: String, targetSize: Int = 1600): BitMatrix {
        val modules = MultiFormatWriter().encode(contents, BarcodeFormat.QR_CODE, 0, 0)
        val scale = (targetSize / modules.width).coerceAtLeast(1)
        return BitMatrix(modules.width * scale).apply {
            for (y in 0 until modules.height) {
                for (x in 0 until modules.width) {
                    if (modules[x, y]) setRegion(x * scale, y * scale, scale, scale)
                }
            }
        }
    }
}
