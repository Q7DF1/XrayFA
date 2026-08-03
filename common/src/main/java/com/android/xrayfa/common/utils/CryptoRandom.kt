package com.android.xrayfa.common.utils

/**
 * Cryptographically secure random source for shared logic.
 *
 * JVM uses [JvmCryptoRandom] (java.security.SecureRandom); iOS will use
 * SecRandomCopyBytes via expect/actual when this module becomes KMP.
 */
interface CryptoRandom {
    fun nextBytes(buffer: ByteArray)

    /** @return a uniformly distributed value in `[0, n)`. */
    fun nextInt(n: Int): Int
}

internal val defaultCryptoRandom: CryptoRandom = JvmCryptoRandom()
