package com.android.xrayfa.common.core

/**
 * Platform file paths for Xray runtime assets (geo databases, etc.).
 *
 * Android actual uses [Context.filesDir]; iOS will use App Group / Documents.
 */
interface XrayAssetPaths {
    /** Absolute path to geoip.dat */
    val geoIpPath: String

    /** Absolute path to geosite.dat */
    val geoSitePath: String

    /** Absolute path to GeoLite2-Country.mmdb */
    val geoLiteDatabasePath: String
}
