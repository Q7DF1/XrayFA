package com.android.xrayfa.core

/** Counts bytes from the core's existing read-and-reset snapshot, without a second native query. */
data class VpnSessionTraffic(
    val startedAtElapsed: Long = 0,
    val available: Boolean = false,
    val uploadedBytes: Long = 0,
    val downloadedBytes: Long = 0,
) {
    fun add(uploaded: Long, downloaded: Long): VpnSessionTraffic = copy(
        uploadedBytes = uploadedBytes + uploaded.coerceAtLeast(0),
        downloadedBytes = downloadedBytes + downloaded.coerceAtLeast(0),
    )
}
