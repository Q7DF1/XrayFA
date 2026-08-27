package com.android.xrayfa.common.utils

/**
 * Platform crypto factories for shared logic.
 *
 * Android actual: [JvmCryptoRandom] / [JvmDigestCalculator] (java.security).
 * iOS actual: SecRandomCopyBytes / CommonCrypto (SHA-256, MD5).
 */
internal expect val defaultCryptoRandom: CryptoRandom

internal expect val defaultDigestCalculator: DigestCalculator
