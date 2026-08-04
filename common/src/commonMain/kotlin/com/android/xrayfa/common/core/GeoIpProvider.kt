package com.android.xrayfa.common.core

/**
 * Resolves a server IP to a display country indicator (flag emoji).
 *
 * Android actual uses MaxMind GeoLite2; iOS will use a platform MMDB reader later.
 */
interface GeoIpProvider {
    /** @return flag emoji for the IP, or empty string when lookup fails or geo DB is unavailable */
    fun countryIsoFromIp(ip: String): String
}
