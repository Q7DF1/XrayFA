package com.android.xrayfa.common.core

/** Owns the partial file until validation completes; the installed database is never truncated. */
class CityDatabaseInstaller(
    destination: String,
    download: suspend (String, String, (Float) -> Unit) -> Unit,
    read: (String) -> ByteArray? = AssetFiles::read,
    validate: (ByteArray) -> Unit = { MmdbCityLookup(it) },
    replace: (String, String) -> Unit = AssetFiles::replace,
    delete: (String) -> Unit = AssetFiles::delete,
) {
    private val installer = AtomicAssetInstaller(
        destination = destination,
        url = DOWNLOAD_URL,
        download = download,
        validate = { path -> validate(read(path) ?: error("Missing City database")) },
        replace = replace,
        delete = delete,
    )

    fun clearPartialDownload() = installer.clearPartialDownload()

    suspend fun install(onProgress: (Float) -> Unit = {}) = installer.install(onProgress)

    companion object {
        const val DOWNLOAD_URL =
            "https://raw.githubusercontent.com/P3TERX/GeoLite.mmdb/download/GeoLite2-City.mmdb"
    }
}
