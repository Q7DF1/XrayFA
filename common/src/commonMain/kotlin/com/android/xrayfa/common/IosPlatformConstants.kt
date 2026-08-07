package com.android.xrayfa.common

/** Shared identifiers for iOS App ↔ Network Extension IPC (App Group). */
object IosPlatformConstants {
    const val APP_GROUP_ID: String = "group.com.android.xrayfa"
    const val PACKET_TUNNEL_BUNDLE_ID: String = "com.android.xrayfa.ios.PacketTunnel"

    /** Xray JSON config written by the host app before starting the VPN tunnel. */
    const val VPN_PENDING_CONFIG_KEY: String = "pending_vpn_config_json"

    /** `"true"` / `"false"` — Network Extension updates; host app observes for connection state. */
    const val VPN_TUNNEL_CONNECTED_KEY: String = "vpn_tunnel_connected"
}
