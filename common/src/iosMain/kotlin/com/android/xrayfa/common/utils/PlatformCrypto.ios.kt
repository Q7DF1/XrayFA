@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.android.xrayfa.common.utils

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CC_LONG
import platform.CoreCrypto.CC_MD5
import platform.CoreCrypto.CC_MD5_DIGEST_LENGTH
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault

internal actual val defaultCryptoRandom: CryptoRandom = IosCryptoRandom()

internal actual val defaultDigestCalculator: DigestCalculator = IosDigestCalculator()

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

private class IosDigestCalculator : DigestCalculator {
    override fun createDigest(algorithm: String): StreamingDigest {
        val hash: (ByteArray) -> ByteArray =
            when (algorithm) {
                "SHA-256" -> ::ccSha256
                "MD5" -> ::ccMd5
                else -> error("Unsupported digest algorithm: $algorithm")
            }
        return BufferingStreamingDigest(hash)
    }
}

private class BufferingStreamingDigest(
    private val hash: (ByteArray) -> ByteArray,
) : StreamingDigest {
    private val chunks = ArrayList<ByteArray>()
    private var total = 0

    override fun update(buffer: ByteArray, offset: Int, length: Int) {
        if (length <= 0) return
        chunks.add(buffer.copyOfRange(offset, offset + length))
        total += length
    }

    override fun finalize(): ByteArray {
        val data = ByteArray(total)
        var index = 0
        for (chunk in chunks) {
            chunk.copyInto(data, index)
            index += chunk.size
        }
        chunks.clear()
        total = 0
        return hash(data)
    }
}

private fun ccSha256(data: ByteArray): ByteArray =
    commonDigest(data, CC_SHA256_DIGEST_LENGTH) { input, len, output ->
        CC_SHA256(input, len, output)
    }

@Suppress("DEPRECATION")
private fun ccMd5(data: ByteArray): ByteArray =
    commonDigest(data, CC_MD5_DIGEST_LENGTH) { input, len, output ->
        CC_MD5(input, len, output)
    }

private inline fun commonDigest(
    data: ByteArray,
    digestLength: Int,
    nativeHash: (input: CPointer<*>?, len: CC_LONG, output: CPointer<UByteVar>) -> Unit,
): ByteArray {
    val out = ByteArray(digestLength)
    out.usePinned { outPinned ->
        val output = outPinned.addressOf(0).reinterpret<UByteVar>()
        if (data.isEmpty()) {
            nativeHash(null, 0u, output)
        } else {
            data.usePinned { inPinned ->
                nativeHash(inPinned.addressOf(0), data.size.toUInt(), output)
            }
        }
    }
    return out
}
