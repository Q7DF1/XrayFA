package com.android.xrayfa.common.core

/**
 * Platform file paths for Xray runtime assets (geo databases, etc.).
 *
 * Android actual uses [Context.filesDir]; iOS will use App Group / Documents.
 */
interface XrayAssetPaths {
    /** Absolute path to the Xray runtime working directory (geo assets, core env). */
    val basePath: String

    /** Absolute path to geoip.dat */
    val geoIpPath: String

    /** Absolute path to geosite.dat */
    val geoSitePath: String

    /** Absolute path to GeoLite2-Country.mmdb */
    val geoLiteDatabasePath: String
}
