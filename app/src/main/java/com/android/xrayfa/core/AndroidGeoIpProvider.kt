package com.android.xrayfa.core

import android.util.Log
import com.android.xrayfa.common.core.GeoIpProvider
import com.android.xrayfa.common.core.XrayAssetPaths
import com.maxmind.geoip2.DatabaseReader
import java.io.File
import java.net.InetAddress

class AndroidGeoIpProvider(
    private val assetPaths: XrayAssetPaths,
) : GeoIpProvider {

    override fun countryIsoFromIp(ip: String): String {
        return try {
            val file = File(assetPaths.geoLiteDatabasePath)
            val reader = DatabaseReader.Builder(file).build()
            val address = InetAddress.getByName(ip)
            val res = reader.country(address)
            countryCodeToEmoji(res.country.isoCode ?: "")
        } catch (e: Exception) {
            Log.e(TAG, "countryIsoFromIp: parse ip failed: ${e.message}")
            ""
        }
    }

    private fun countryCodeToEmoji(countryCode: String): String {
        val code = countryCode.uppercase()
        if (code.length != 2) return "❓"
        val flagOffset = 0x1F1E6
        val asciiOffset = 0x41
        val firstChar = Character.codePointAt(code, 0) - asciiOffset + flagOffset
        val secondChar = Character.codePointAt(code, 1) - asciiOffset + flagOffset
        return String(Character.toChars(firstChar)) + String(Character.toChars(secondChar))
    }

    private companion object {
        const val TAG = "AndroidGeoIpProvider"
    }
}
