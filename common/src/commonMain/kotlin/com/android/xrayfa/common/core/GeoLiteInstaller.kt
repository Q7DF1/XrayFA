package com.android.xrayfa.common.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

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
    destPath: String,
    download: suspend (url: String, destPath: String, onProgress: (Float) -> Unit) -> Unit,
    private val setInstalled: suspend (Boolean) -> Unit,
    read: (String) -> ByteArray? = AssetFiles::read,
    validate: (ByteArray) -> Unit = {
        require(MmdbReader(it).databaseType.contains("Country", ignoreCase = true)) { "A Country MMDB is required" }
    },
    replace: (String, String) -> Unit = AssetFiles::replace,
    delete: (String) -> Unit = AssetFiles::delete,
) {
    private val installer = AtomicAssetInstaller(
        destination = destPath,
        url = GeoLiteAsset.DOWNLOAD_URL,
        download = download,
        validate = { path -> validate(read(path) ?: error("Missing Country database")) },
        replace = replace,
        delete = delete,
    )

    fun clearPartialDownload() = installer.clearPartialDownload()

    suspend fun install(onProgress: (Float) -> Unit = {}): Boolean {
        return try {
            installer.install(onProgress)
            // Once committed, finish persisting its installed flag even if the caller is cancelled.
            withContext(NonCancellable) { setInstalled(true) }
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
    }
}

fun geoLiteDownloadEnabled(
    vpnConnected: Boolean,
    downloading: Boolean,
    downloadSupported: Boolean = true,
): Boolean = downloadSupported && vpnConnected && !downloading

/** Android can proxy GeoLite through in-process SOCKS; iOS cannot reach NE loopback. */
expect val geoLiteDownloadSupported: Boolean
