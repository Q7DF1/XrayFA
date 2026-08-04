package com.android.xrayfa.common.utils

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Locks JVM crypto abstractions to JDK behavior for future KMP expect/actual migration.
 * See docs/KMP_MIGRATION_STEP4_HANDOVER.md.
 */
class CryptoUtilsTest {

    @Test
    fun calculateBytesHash_matchesJdkSha256() {
        val data = "hello geo file".encodeToByteArray()
        val expected = MessageDigest.getInstance("SHA-256").digest(data).toHexLowercase()
        assertEquals(expected, calculateBytesHash(data, "SHA-256"))
    }

    @Test
    fun calculateBytesHash_matchesJdkMd5() {
        val data = byteArrayOf(0, 1, 2, 3, 4, 5)
        val expected = MessageDigest.getInstance("MD5").digest(data).toHexLowercase()
        assertEquals(expected, calculateBytesHash(data, "MD5"))
    }

    @Test
    fun jvmCryptoRandom_nextInt_matchesSecureRandomDistributionBounds() {
        val jdk = SecureRandom()
        val ours = JvmCryptoRandom()
        repeat(100) {
            val n = 64512
            val jdkValue = jdk.nextInt(n)
            val ourValue = ours.nextInt(n)
            assertTrue(jdkValue in 0 until n)
            assertTrue(ourValue in 0 until n)
        }
    }

    @Test
    fun jvmCryptoRandom_nextBytes_producesRequestedLength() {
        val ours = JvmCryptoRandom()
        val buffer = ByteArray(32)
        ours.nextBytes(buffer)
        assertEquals(32, buffer.size)
    }

    @Test
    fun jvmStreamingDigest_incrementalMatchesOneShot() {
        val data = "chunked-input".encodeToByteArray()
        val chunkSize = 4

        val oneShot = MessageDigest.getInstance("SHA-256").digest(data)

        val digest = JvmDigestCalculator().createDigest("SHA-256")
        var offset = 0
        while (offset < data.size) {
            val len = minOf(chunkSize, data.size - offset)
            digest.update(data, offset, len)
            offset += len
        }
        assertArrayEquals(oneShot, digest.finalize())
    }

    @Test
    fun socksConfigGenerator_portInRange() {
        repeat(50) {
            assertTrue(SocksConfigGenerator.generatePort() in SocksConfigGenerator.portRange)
        }
    }

    @Test
    fun socksConfigGenerator_credentialLengths() {
        assertEquals(16, SocksConfigGenerator.generatePassword().length)
        assertEquals(8, SocksConfigGenerator.generateUsername().length)
    }
}
