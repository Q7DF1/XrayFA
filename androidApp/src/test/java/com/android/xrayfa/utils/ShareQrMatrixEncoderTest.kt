package com.android.xrayfa.utils

import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.assertEquals
import org.junit.Test

class ShareQrMatrixEncoderTest {
    @Test
    fun shortAndDenseLinksKeepExactlyFourQuietModulesAndDecode() {
        for (length in listOf(50, 200, 500, 1000, 1800, 2500)) {
            val content = "https://example.com/share?data=" + "a".repeat(length)
            val matrix = ShareQrMatrixEncoder.encode(content)
            val bounds = matrix.enclosingRectangle
            // Top-left finder is seven modules wide.
            val left = bounds[0]
            var finderWidth = 0
            while (matrix[left + finderWidth, bounds[1]]) finderWidth++
            val module = finderWidth / 7
            assertEquals(4 * module, left)
            assertEquals(4 * module, bounds[1])
            assertEquals(4 * module, matrix.width - left - bounds[2])
            assertEquals(4 * module, matrix.height - bounds[1] - bounds[3])
            val pixels = IntArray(matrix.width * matrix.height) { index ->
                if (matrix[index % matrix.width, index / matrix.width]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            }
            val source = RGBLuminanceSource(matrix.width, matrix.height, pixels)
            assertEquals(content, QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source))).text)
        }
    }
}
