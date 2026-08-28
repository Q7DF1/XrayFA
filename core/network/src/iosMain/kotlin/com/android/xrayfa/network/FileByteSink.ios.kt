@file:OptIn(ExperimentalForeignApi::class)

package com.android.xrayfa.network

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fwrite

actual class FileByteSink actual constructor(path: String) {
    private val file =
        fopen(path, "wb") ?: error("Cannot open $path for write")

    actual fun write(buffer: ByteArray, offset: Int, length: Int) {
        if (length <= 0) return
        val slice = if (offset == 0 && length == buffer.size) buffer else buffer.copyOfRange(offset, offset + length)
        slice.usePinned { pinned ->
            fwrite(pinned.addressOf(0), 1u, slice.size.toULong(), file)
        }
    }

    actual fun close() {
        fclose(file)
    }
}
