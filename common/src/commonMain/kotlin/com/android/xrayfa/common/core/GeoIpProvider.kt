package com.android.xrayfa.common.core

/**
 * Resolves a server IP to a display country indicator (flag emoji).
 *
 * Both platforms read GeoLite2-Country.mmdb via [MmdbCountryLookup].
 */
interface GeoIpProvider {
    /** @return flag emoji for the IP, or empty string when lookup fails or geo DB is unavailable */
    fun countryIsoFromIp(ip: String): String
}
