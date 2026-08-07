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
}

/** Host app writes Xray JSON here before [IosVpnController.connect]. */
fun IosVpnController.setPendingConfig(configJson: String) {
    IosAppGroupStorage.writePendingConfig(configJson)
}
