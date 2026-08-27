package com.android.xrayfa.network

import java.io.File
import java.io.FileOutputStream

actual class FileByteSink actual constructor(path: String) {
    private val stream: FileOutputStream = File(path).outputStream()

    actual fun write(buffer: ByteArray, offset: Int, length: Int) {
        stream.write(buffer, offset, length)
    }

    actual fun close() {
        stream.close()
    }
}
