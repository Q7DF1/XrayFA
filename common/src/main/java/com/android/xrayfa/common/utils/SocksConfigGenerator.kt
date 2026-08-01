package com.android.xrayfa.common.utils
import java.security.SecureRandom

/**
 * Configuration generator for SOCKS5 proxy to prevent detection.
 * Logic based on discussions in v2rayNG Issue #5457.
 */
object SocksConfigGenerator {

    private val secureRandom = SecureRandom()

    // Recommended range for dynamic/private ports
    val portRange = 1024..65535

    /**
     * Generates a random port and secure credentials.
     */
    fun generatePassword(): String {
        return generateRandomString(16)
    }
    fun generateUsername(): String {
        return generateRandomString(8)
    }
    fun generatePort(): Int {
        return secureRandom.nextInt(portRange.last - portRange.first + 1) + portRange.first
    }

    /**
     * Generates a cryptographically secure random string.
     */
    private fun generateRandomString(length: Int): String {
        val bytes = ByteArray(length)
        secureRandom.nextBytes(bytes)
        // Use URL-safe Base64 to avoid issues with special characters in config files
        return Base64Compat.encodeUrlSafeNoPadding(bytes).take(length)
    }
}