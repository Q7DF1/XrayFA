@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.android.xrayfa.common.utils

import com.android.xrayfa.linux.crypto.*
import kotlinx.cinterop.*
import platform.posix.*

internal actual val defaultCryptoRandom: CryptoRandom = object : CryptoRandom {
    override fun nextBytes(buffer: ByteArray) {
        if (buffer.isEmpty()) return
        val file = fopen("/dev/urandom", "rb") ?: error("Random source unavailable")
        try { buffer.usePinned { check(fread(it.addressOf(0), 1u, buffer.size.convert(), file).toInt() == buffer.size) } }
        finally { fclose(file) }
    }
    override fun nextInt(n: Int): Int {
        require(n > 0)
        val bytes = ByteArray(4)
        val bound = (1L shl 32) - (1L shl 32) % n
        while (true) {
            nextBytes(bytes)
            val value = bytes.fold(0L) { a, b -> (a shl 8) or (b.toLong() and 255) }
            if (value < bound) return (value % n).toInt()
        }
    }
}

internal actual val defaultDigestCalculator: DigestCalculator = object : DigestCalculator {
    override fun createDigest(algorithm: String): StreamingDigest {
        val md = when (algorithm) { "SHA-256" -> xf_sha256(); "MD5" -> xf_md5(); else -> error("Unsupported digest") }
        val ctx = xf_md_new() ?: error("Digest allocation failed")
        if (xf_md_init(ctx, md, null) != 1) { xf_md_free(ctx); error("Digest initialization failed") }
        return object : StreamingDigest {
            var finished = false
            override fun update(buffer: ByteArray, offset: Int, length: Int) {
                check(!finished)
                require(offset >= 0 && length >= 0 && offset <= buffer.size - length)
                if (length > 0) buffer.usePinned { check(xf_md_update(ctx, it.addressOf(offset), length.convert()) == 1) }
            }
            override fun finalize(): ByteArray {
                check(!finished); finished = true
                try {
                    val output = ByteArray(64)
                    return memScoped {
                        val size = alloc<UIntVar>()
                        output.usePinned { check(xf_md_final(ctx, it.addressOf(0).reinterpret(), size.ptr) == 1) }
                        output.copyOf(size.value.toInt())
                    }
                } finally { xf_md_free(ctx) }
            }
        }
    }
}

/** Linux-only streaming entry point used to hash external executables without buffering. */
object LinuxDigest {
    fun create(algorithm: String): StreamingDigest = defaultDigestCalculator.createDigest(algorithm)
}
