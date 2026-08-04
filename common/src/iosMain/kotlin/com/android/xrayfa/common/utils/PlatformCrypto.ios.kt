@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.android.xrayfa.common.utils

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault

internal actual val defaultCryptoRandom: CryptoRandom = IosCryptoRandom()

/**
 * Compile-time shell for iOS digest; CommonCrypto cinterop wiring is deferred to a later step.
 * Android behavior is unchanged via [JvmDigestCalculator].
 */
internal actual val defaultDigestCalculator: DigestCalculator = IosDigestCalculatorStub()

private class IosCryptoRandom : CryptoRandom {
    override fun nextBytes(buffer: ByteArray) {
        buffer.usePinned { pinned ->
            val status = SecRandomCopyBytes(
                kSecRandomDefault,
                buffer.size.convert(),
                pinned.addressOf(0),
            )
            check(status == 0) { "SecRandomCopyBytes failed with status $status" }
        }
    }

    override fun nextInt(n: Int): Int {
        require(n > 0) { "n must be positive" }
        val bytes = ByteArray(4)
        nextBytes(bytes)
        val value = bytes.fold(0) { acc, b -> (acc shl 8) or (b.toInt() and 0xFF) }
        return (value and Int.MAX_VALUE) % n
    }
}

private class IosDigestCalculatorStub : DigestCalculator {
    override fun createDigest(algorithm: String): StreamingDigest = IosStreamingDigestStub()
}

private class IosStreamingDigestStub : StreamingDigest {
    override fun update(buffer: ByteArray, offset: Int, length: Int) = Unit

    override fun finalize(): ByteArray = ByteArray(0)
}
