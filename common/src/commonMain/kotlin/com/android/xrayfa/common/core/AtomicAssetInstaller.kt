package com.android.xrayfa.common.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Downloads to a partial file, validates it, then replaces the installed asset atomically. */
class AtomicAssetInstaller(
    private val destination: String,
    private val url: String,
    private val download: suspend (String, String, (Float) -> Unit) -> Unit,
    private val validate: (String) -> Unit,
    private val replace: (String, String) -> Unit = AssetFiles::replace,
    private val delete: (String) -> Unit = AssetFiles::delete,
) {
    private val partialPath = "$destination.download"

    fun clearPartialDownload() = delete(partialPath)

    suspend fun install(onProgress: (Float) -> Unit = {}) {
        try {
            clearPartialDownload()
            download(url, partialPath, onProgress)
            withContext(Dispatchers.Default) { validate(partialPath) }
            currentCoroutineContext().ensureActive()
            // No suspension between the commit and return: callers can publish their new state.
            replace(partialPath, destination)
        } finally {
            clearPartialDownload()
        }
    }
}
