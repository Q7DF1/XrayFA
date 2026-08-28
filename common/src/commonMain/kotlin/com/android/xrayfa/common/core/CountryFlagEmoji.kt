package com.android.xrayfa.common.core

/**
 * ISO 3166-1 alpha-2 → regional-indicator flag emoji.
 * Invalid length (including empty) maps to ❓, matching Android GeoIP display.
 */
object CountryFlagEmoji {
    private const val FLAG_OFFSET = 0x1F1E6
    private const val ASCII_OFFSET = 0x41

    fun fromIsoCode(countryCode: String): String {
        val code = countryCode.uppercase()
        if (code.length != 2) return "❓"
        val first = code[0].code - ASCII_OFFSET + FLAG_OFFSET
        val second = code[1].code - ASCII_OFFSET + FLAG_OFFSET
        return codePointToString(first) + codePointToString(second)
    }

    private fun codePointToString(codePoint: Int): String {
        if (codePoint <= 0xFFFF) return codePoint.toChar().toString()
        val offset = codePoint - 0x10000
        return charArrayOf(
            ((offset ushr 10) + 0xD800).toChar(),
            ((offset and 0x3FF) + 0xDC00).toChar(),
        ).concatToString()
    }
}
