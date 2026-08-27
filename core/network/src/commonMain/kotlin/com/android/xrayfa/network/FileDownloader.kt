package com.android.xrayfa.network

import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable

class FileDownloader(
    private val httpClient: HttpClient,
) {
    suspend fun download(
        url: String,
        onProgress: (loaded: Long, total: Long?) -> Unit = { _, _ -> },
        receiveBytes: suspend (buffer: ByteArray, offset: Int, length: Int) -> Unit,
    ) {
        httpClient.prepareGet(url).execute { response ->
            if (!response.status.isSuccess()) {
                throw FileDownloadException("HTTP error: ${response.status.value}")
            }
            val contentLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()?.takeIf { it > 0 }
            val channel = response.bodyAsChannel()
            val buffer = ByteArray(8192)
            var totalRead = 0L
            while (!channel.isClosedForRead) {
                val read = channel.readAvailable(buffer, 0, buffer.size)
                if (read <= 0) break
                receiveBytes(buffer, 0, read)
                totalRead += read
                onProgress(totalRead, contentLength)
            }
        }
    }

    suspend fun downloadToFile(
        url: String,
        targetPath: String,
        onProgress: (Float) -> Unit = {},
    ) {
        val sink = FileByteSink(targetPath)
        try {
            download(
                url = url,
                onProgress = { loaded, total ->
                    if (total != null && total > 0) {
                        onProgress(loaded.toFloat() / total)
                    }
                },
            ) { buffer, offset, length ->
                sink.write(buffer, offset, length)
            }
        } finally {
            sink.close()
        }
    }
}

class FileDownloadException(message: String) : Exception(message)
