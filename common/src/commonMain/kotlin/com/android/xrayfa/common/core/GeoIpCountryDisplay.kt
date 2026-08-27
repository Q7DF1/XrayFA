package com.android.xrayfa.common.core

/**
 * Maps a GeoLite country ISO code to the string parsers store on the node.
 *
 * - `null` (lookup failed / not in DB / missing file) → `""`
 * - present but not a 2-letter code → `❓`
 * - 2-letter ISO → flag emoji
 */
object GeoIpCountryDisplay {
    fun fromIsoCode(isoCode: String?): String =
        if (isoCode == null) "" else CountryFlagEmoji.fromIsoCode(isoCode)
}
