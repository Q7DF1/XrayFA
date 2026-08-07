package com.android.xrayfa.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.net.Uri
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.MultiFormatWriter
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.HybridBinarizer
import java.util.EnumMap
import kotlin.collections.set
object BarcodeUtils {
    const val TAG = "BarcodeUtils"

    const val BG_COLOR: Int = 0xFFFFFFFF.toInt()
    const val FG_COLOR: Int = 0xFF000000.toInt()

    fun encode(contents: String, format: BarcodeFormat, width: Int, height: Int): BitMatrix? {
        return try {
            MultiFormatWriter().encode(contents, format, width, height)
        } catch (e: Exception) {
            Log.i(TAG, "encode: throw Error ${e.message}")
            null
        }
    }

    fun createBitmap(matrix: BitMatrix): Bitmap {
        val width = matrix.width
        val height = matrix.height
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val offset = y * width
            for (x in 0 until width) {
                pixels[offset + x] = if (matrix.get(x,y)) FG_COLOR else BG_COLOR
            }
        }
        val bitmap = Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    fun encodeBitmap(contents: String, format: BarcodeFormat, width: Int, height: Int): Bitmap? {
        val matrixOrNull= encode(contents,format,width,height)
        return matrixOrNull?.run { createBitmap(matrixOrNull) }
    }

    fun decodeQRCodeFromUri(context: Context, uri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val bitmap = BitmapFactory.decodeStream(inputStream) ?: return null

            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

            val source = RGBLuminanceSource(width, height, pixels)
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))

            val hints = EnumMap<DecodeHintType, Any>(DecodeHintType::class.java)
            hints[DecodeHintType.TRY_HARDER] = true
            hints[DecodeHintType.POSSIBLE_FORMATS] = listOf(BarcodeFormat.QR_CODE)
            hints[DecodeHintType.CHARACTER_SET] = "utf-8"

            MultiFormatReader().decode(binaryBitmap, hints).text
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun decodeQRCodeFromImageProxy(reader: MultiFormatReader, imageProxy: ImageProxy): String? {
        if (imageProxy.format != ImageFormat.YUV_420_888) return null

        val plane = imageProxy.planes[0]
        val buffer = plane.buffer
        val data = ByteArray(buffer.remaining())
        buffer.get(data)

        // 使用 rowStride 确保 PlanarYUVLuminanceSource 正确解析
        val source = PlanarYUVLuminanceSource(
            data, plane.rowStride, imageProxy.height,
            0, 0, imageProxy.width, imageProxy.height, false
        )
        val binaryBitmap = BinaryBitmap(HybridBinarizer(source))

        return try {
            reader.decodeWithState(binaryBitmap).text
        } catch (e: Exception) {
            null
        } finally {
            reader.reset()
        }
    }
}