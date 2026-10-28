package com.android.xrayfa.common.core

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

actual object AssetFiles {
    actual fun read(path: String): ByteArray? = File(path).takeIf { it.isFile }?.let {
        require(it.length() in 1..MAX_MMDB_BYTES)
        it.readBytes()
    }

    actual fun replace(source: String, destination: String) {
        Files.move(
            File(source).toPath(),
            File(destination).toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
    }

    actual fun delete(path: String) { Files.deleteIfExists(File(path).toPath()) }
}
