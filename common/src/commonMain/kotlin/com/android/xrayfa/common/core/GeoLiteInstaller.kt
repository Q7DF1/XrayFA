package com.android.xrayfa.common.core

/** Official GeoLite2-Country MMDB used for node country flags. */
object GeoLiteAsset {
    const val DOWNLOAD_URL =
        "https://github.com/P3TERX/GeoLite.mmdb/raw/download/GeoLite2-Country.mmdb"
}

/**
 * Downloads GeoLite to [destPath] and only then marks [geoLiteInstall] true.
 * Failures leave the install flag unchanged (Android previously set it even on error).
 */
class GeoLiteInstaller(
    private val destPath: String,
    private val download: suspend (url: String, destPath: String, onProgress: (Float) -> Unit) -> Unit,
    private val setInstalled: suspend (Boolean) -> Unit,
) {
    suspend fun install(onProgress: (Float) -> Unit = {}): Boolean {
        return try {
            download(GeoLiteAsset.DOWNLOAD_URL, destPath, onProgress)
            setInstalled(true)
            true
        } catch (_: Exception) {
            false
        }
    }
}

fun geoLiteDownloadEnabled(vpnConnected: Boolean, downloading: Boolean): Boolean =
    vpnConnected && !downloading
