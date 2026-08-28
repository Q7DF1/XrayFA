package com.android.xrayfa.common.utils

import java.security.SecureRandom

internal class JvmCryptoRandom : CryptoRandom {
    private val delegate = SecureRandom()

    override fun nextBytes(buffer: ByteArray) {
        delegate.nextBytes(buffer)
    }

    override fun nextInt(n: Int): Int = delegate.nextInt(n)
}
