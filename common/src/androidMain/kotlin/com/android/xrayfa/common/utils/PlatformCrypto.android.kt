package com.android.xrayfa.common.utils

internal actual val defaultCryptoRandom: CryptoRandom = JvmCryptoRandom()

internal actual val defaultDigestCalculator: DigestCalculator = JvmDigestCalculator()
