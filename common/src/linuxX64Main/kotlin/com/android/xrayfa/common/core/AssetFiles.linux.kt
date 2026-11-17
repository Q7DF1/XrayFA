@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.android.xrayfa.common.core

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.posix.access
import platform.posix.F_OK
import platform.posix.SEEK_END
import platform.posix.SEEK_SET
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.ftell
import platform.posix.remove
import platform.posix.rename

actual object AssetFiles {
    actual fun read(path: String): ByteArray? {
        val file = fopen(path, "rb") ?: return null
        try {
            check(fseek(file, 0, SEEK_END) == 0)
            val size = ftell(file)
            require(size in 1..MAX_MMDB_BYTES)
            check(fseek(file, 0, SEEK_SET) == 0)
            return ByteArray(size.toInt()).also { bytes ->
                bytes.usePinned { check(fread(it.addressOf(0), 1u, size.toULong(), file).toLong() == size) }
            }
        } finally { fclose(file) }
    }

    actual fun replace(source: String, destination: String) { check(rename(source, destination) == 0) }
    actual fun delete(path: String) {
        if (access(path, F_OK) == 0) check(remove(path) == 0)
    }
}

actual val geoLiteDownloadSupported: Boolean = false
