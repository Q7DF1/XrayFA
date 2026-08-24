@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.android.xrayfa.vpn

import com.android.xrayfa.common.IosPlatformConstants
import platform.Foundation.NSUserDefaults

/** App Group UserDefaults for host app ↔ PacketTunnel IPC. */
internal object IosAppGroupStorage {
    private val defaults: NSUserDefaults?
        get() = NSUserDefaults(suiteName = IosPlatformConstants.APP_GROUP_ID)

    fun writePendingConfig(configJson: String) {
        defaults?.setObject(configJson, IosPlatformConstants.VPN_PENDING_CONFIG_KEY)
        defaults?.synchronize()
    }

    fun readPendingConfig(): String? =
        defaults?.stringForKey(IosPlatformConstants.VPN_PENDING_CONFIG_KEY)

    fun setTunnelConnected(connected: Boolean) {
        defaults?.setObject(
            if (connected) "true" else "false",
            IosPlatformConstants.VPN_TUNNEL_CONNECTED_KEY,
        )
        defaults?.synchronize()
    }

    fun isTunnelConnected(): Boolean =
        defaults?.stringForKey(IosPlatformConstants.VPN_TUNNEL_CONNECTED_KEY) == "true"

    fun writeTrafficSpeedsKbps(
        uploadKbps: Double,
        downloadKbps: Double,
    ) {
        defaults?.setDouble(uploadKbps, IosPlatformConstants.VPN_UPLOAD_SPEED_KBPS_KEY)
        defaults?.setDouble(downloadKbps, IosPlatformConstants.VPN_DOWNLOAD_SPEED_KBPS_KEY)
        defaults?.synchronize()
    }

    fun readTrafficSpeedsKbps(): Pair<Double, Double> {
        val up = defaults?.doubleForKey(IosPlatformConstants.VPN_UPLOAD_SPEED_KBPS_KEY) ?: 0.0
        val down = defaults?.doubleForKey(IosPlatformConstants.VPN_DOWNLOAD_SPEED_KBPS_KEY) ?: 0.0
        return up to down
    }

    fun clearTrafficSpeeds() {
        writeTrafficSpeedsKbps(0.0, 0.0)
    }

    fun writeTunnelLastError(message: String) {
        defaults?.setObject(message, IosPlatformConstants.VPN_TUNNEL_LAST_ERROR_KEY)
        defaults?.synchronize()
    }

    fun readTunnelLastError(): String? =
        defaults?.stringForKey(IosPlatformConstants.VPN_TUNNEL_LAST_ERROR_KEY)

    fun clearTunnelLastError() {
        defaults?.removeObjectForKey(IosPlatformConstants.VPN_TUNNEL_LAST_ERROR_KEY)
        defaults?.synchronize()
    }

    fun writeTunnelStatus(message: String) {
        defaults?.setObject(message, IosPlatformConstants.VPN_TUNNEL_STATUS_KEY)
        defaults?.synchronize()
    }

    fun readTunnelStatus(): String? =
        defaults?.stringForKey(IosPlatformConstants.VPN_TUNNEL_STATUS_KEY)

    fun writeTunnelMemoryBytes(bytes: Long) {
        defaults?.setDouble(bytes.toDouble(), IosPlatformConstants.VPN_TUNNEL_MEMORY_BYTES_KEY)
        defaults?.synchronize()
    }

    fun readTunnelMemoryBytes(): Long =
        defaults?.doubleForKey(IosPlatformConstants.VPN_TUNNEL_MEMORY_BYTES_KEY)?.toLong() ?: 0L
}

/** Host app reads KB/s speeds written by PacketTunnel (mirrors Android [TrafficDetector]). */
fun readVpnTrafficSpeedsKbps(): Pair<Double, Double> = IosAppGroupStorage.readTrafficSpeedsKbps()

/** Host app writes Xray JSON here before [IosVpnController.connect]. */
fun IosVpnController.setPendingConfig(configJson: String) {
    IosAppGroupStorage.writePendingConfig(configJson)
}
