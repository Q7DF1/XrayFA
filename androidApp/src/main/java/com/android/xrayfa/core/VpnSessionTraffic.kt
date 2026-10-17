package com.android.xrayfa.core

/** Counts bytes from the core's existing read-and-reset snapshot, without a second native query. */
data class VpnSessionTraffic(
    val startedAtElapsed: Long = 0,
    val available: Boolean = false,
    val uploadedBytes: Long = 0,
    val downloadedBytes: Long = 0,
    val uploadBytesPerSecond: Double = 0.0,
    val downloadBytesPerSecond: Double = 0.0,
) {
    fun add(
        uploaded: Long,
        downloaded: Long,
        uploadRate: Double = 0.0,
        downloadRate: Double = 0.0,
    ): VpnSessionTraffic = copy(
        uploadedBytes = uploadedBytes + uploaded.coerceAtLeast(0),
        downloadedBytes = downloadedBytes + downloaded.coerceAtLeast(0),
        uploadBytesPerSecond = uploadRate.coerceAtLeast(0.0),
        downloadBytesPerSecond = downloadRate.coerceAtLeast(0.0),
    )
}
