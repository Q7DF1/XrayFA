package com.android.xrayfa.common.utils

import java.io.File
import java.io.FileInputStream
import java.io.InputStream

/**
 * Stream-based hash; [InputStream] is JVM/Android-only and will move to platform source sets in KMP.
 */
fun calculateStreamHash(input: InputStream, algorithm: String = "SHA-256"): String {
    val buffer = ByteArray(8192)
    val digest = defaultDigestCalculator.createDigest(algorithm)
    var bytesRead: Int
    while (input.read(buffer).also { bytesRead = it } != -1) {
        digest.update(buffer, 0, bytesRead)
    }
    return digest.finalize().toHexLowercase()
}

/** File-based hash; [File] is JVM/Android-only and will move to platform source sets in KMP. */
fun calculateFileHash(file: File, algorithm: String = "SHA-256"): String {
    if (!file.exists()) return ""
    FileInputStream(file).use { fis ->
        return calculateStreamHash(fis, algorithm)
    }
}
