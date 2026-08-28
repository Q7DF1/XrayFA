package com.android.xrayfa.common.utils

/**
 * Incremental digest for streaming hash computation.
 * Mirrors java.security.MessageDigest update/finalize semantics without exposing JVM types.
 */
interface StreamingDigest {
    fun update(buffer: ByteArray, offset: Int, length: Int)
    fun finalize(): ByteArray
}

/**
 * Platform digest factory for shared logic.
 *
 * JVM uses [JvmDigestCalculator] (java.security.MessageDigest); iOS uses
 * CommonCrypto (`CC_SHA256` / `CC_MD5`).
 */
interface DigestCalculator {
    fun createDigest(algorithm: String): StreamingDigest
}

private const val HEX_LOWER = "0123456789abcdef"

internal fun ByteArray.toHexLowercase(): String = joinToString("") { byte ->
    val value = byte.toInt() and 0xFF
    "${HEX_LOWER[value shr 4]}${HEX_LOWER[value and 0x0F]}"
}

/** Hash a byte array; algorithm names follow JCA conventions (e.g. "SHA-256", "MD5"). */
fun calculateBytesHash(data: ByteArray, algorithm: String = "SHA-256"): String {
    val digest = defaultDigestCalculator.createDigest(algorithm)
    digest.update(data, 0, data.size)
    return digest.finalize().toHexLowercase()
}
