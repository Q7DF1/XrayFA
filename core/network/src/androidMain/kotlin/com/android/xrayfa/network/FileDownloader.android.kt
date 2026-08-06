package com.android.xrayfa.network

import java.io.File

suspend fun FileDownloader.downloadToFile(
    url: String,
    targetPath: String,
    onProgress: (Float) -> Unit = {},
) {
    val file = File(targetPath)
    file.outputStream().use { outputStream ->
        download(
            url = url,
            onProgress = { loaded, total ->
                if (total != null && total > 0) {
                    onProgress(loaded.toFloat() / total)
                }
            },
        ) { buffer, offset, length ->
            outputStream.write(buffer, offset, length)
        }
    }
}
