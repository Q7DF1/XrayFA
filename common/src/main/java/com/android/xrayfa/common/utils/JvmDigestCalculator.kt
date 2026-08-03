package com.android.xrayfa.common.utils

import java.security.MessageDigest

internal class JvmDigestCalculator : DigestCalculator {
    override fun createDigest(algorithm: String): StreamingDigest {
        return JvmStreamingDigest(MessageDigest.getInstance(algorithm))
    }
}

private class JvmStreamingDigest(
    private val delegate: MessageDigest,
) : StreamingDigest {
    override fun update(buffer: ByteArray, offset: Int, length: Int) {
        delegate.update(buffer, offset, length)
    }

    override fun finalize(): ByteArray = delegate.digest()
}
