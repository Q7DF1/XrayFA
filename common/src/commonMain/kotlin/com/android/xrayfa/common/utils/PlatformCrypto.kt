package com.android.xrayfa.common.utils

/**
 * Platform crypto factories for shared logic.
 *
 * Android actual: [JvmCryptoRandom] / [JvmDigestCalculator] (java.security).
 * iOS actual: SecRandomCopyBytes / CommonCrypto (Step 14 shell).
 */
internal expect val defaultCryptoRandom: CryptoRandom

internal expect val defaultDigestCalculator: DigestCalculator
