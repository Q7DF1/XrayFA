package com.android.xrayfa.network

/** Sequential byte sink for [FileDownloader.downloadToFile]. */
expect class FileByteSink(path: String) {
    fun write(buffer: ByteArray, offset: Int, length: Int)

    fun close()
}
