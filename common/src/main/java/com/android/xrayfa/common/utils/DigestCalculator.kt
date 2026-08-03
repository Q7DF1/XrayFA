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
 * JVM uses [JvmDigestCalculator] (java.security.MessageDigest); iOS will use
 * CommonCrypto via expect/actual when this module becomes KMP.
 */
interface DigestCalculator {
    fun createDigest(algorithm: String): StreamingDigest
}

internal val defaultDigestCalculator: DigestCalculator = JvmDigestCalculator()

internal fun ByteArray.toHexLowercase(): String = joinToString("") { "%02x".format(it) }

/** Hash a byte array; algorithm names follow JCA conventions (e.g. "SHA-256", "MD5"). */
fun calculateBytesHash(data: ByteArray, algorithm: String = "SHA-256"): String {
    val digest = defaultDigestCalculator.createDigest(algorithm)
    digest.update(data, 0, data.size)
    return digest.finalize().toHexLowercase()
}
