package com.android.xrayfa.core

import android.util.Log
import com.android.xrayfa.common.core.GeoIpProvider
import com.android.xrayfa.common.core.MmdbCountryLookup
import com.android.xrayfa.common.core.XrayAssetPaths
import java.io.File
import java.net.InetAddress

class AndroidGeoIpProvider(
    private val assetPaths: XrayAssetPaths,
) : GeoIpProvider {

    override fun countryIsoFromIp(ip: String): String {
        return try {
            val file = File(assetPaths.geoLiteDatabasePath)
            if (!file.isFile) return ""
            val lookupIp = ipForLookup(ip) ?: return ""
            MmdbCountryLookup.countryFlag(file.readBytes(), lookupIp)
        } catch (e: Exception) {
            Log.e(TAG, "countryIsoFromIp: parse ip failed: ${e.message}")
            ""
        }
    }

    private fun ipForLookup(ip: String): String? {
        val trimmed = ip.trim()
        if (trimmed.isEmpty()) return null
        if (MmdbCountryLookup.isIpLiteral(trimmed)) return trimmed
        return InetAddress.getByName(trimmed).hostAddress
    }

    private companion object {
        const val TAG = "AndroidGeoIpProvider"
    }
}
