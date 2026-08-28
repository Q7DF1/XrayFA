package com.android.xrayfa.common.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class DigestCalculatorTest {

    @Test
    fun sha256_emptyAndAbc_matchKnownVectors() {
        assertEquals(SHA256_EMPTY, calculateBytesHash(ByteArray(0), "SHA-256"))
        assertEquals(SHA256_ABC, calculateBytesHash("abc".encodeToByteArray(), "SHA-256"))
    }

    @Test
    fun md5_emptyAndAbc_matchKnownVectors() {
        assertEquals(MD5_EMPTY, calculateBytesHash(ByteArray(0), "MD5"))
        assertEquals(MD5_ABC, calculateBytesHash("abc".encodeToByteArray(), "MD5"))
    }

    @Test
    fun sha256_incrementalMatchesOneShot() {
        val data = "chunked-input".encodeToByteArray()
        val oneShot = calculateBytesHash(data, "SHA-256")
        val digest = defaultDigestCalculator.createDigest("SHA-256")
        var offset = 0
        while (offset < data.size) {
            val len = minOf(4, data.size - offset)
            digest.update(data, offset, len)
            offset += len
        }
        assertEquals(oneShot, digest.finalize().toHexLowercase())
    }

    @Test
    fun unknownAlgorithm_throws() {
        assertFails { calculateBytesHash(byteArrayOf(1), "NOT-A-HASH") }
    }
}

private const val SHA256_EMPTY =
    "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
private const val SHA256_ABC =
    "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
private const val MD5_EMPTY = "d41d8cd98f00b204e9800998ecf8427e"
private const val MD5_ABC = "900150983cd24fb0d6963f7d28e17f72"
