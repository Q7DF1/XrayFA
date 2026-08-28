package com.android.xrayfa.common

/** Shared identifiers for iOS App ↔ Network Extension IPC (App Group). */
object IosPlatformConstants {
    const val APP_GROUP_ID: String = "group.com.android.xrayfa"
    const val PACKET_TUNNEL_BUNDLE_ID: String = "com.android.xrayfa.ios.PacketTunnel"

    /** Xray JSON config written by the host app before starting the VPN tunnel. */
    const val VPN_PENDING_CONFIG_KEY: String = "pending_vpn_config_json"

    /** `"true"` / `"false"` — Network Extension updates; host app observes for connection state. */
    const val VPN_TUNNEL_CONNECTED_KEY: String = "vpn_tunnel_connected"

    /** Upload speed in KB/s — PacketTunnel writes; host app polls for home traffic UI. */
    const val VPN_UPLOAD_SPEED_KBPS_KEY: String = "vpn_upload_speed_kbps"

    /** Download speed in KB/s — PacketTunnel writes; host app polls for home traffic UI. */
    const val VPN_DOWNLOAD_SPEED_KBPS_KEY: String = "vpn_download_speed_kbps"

    /** Last tunnel start/runtime error — PacketTunnel writes; host app reads after failed connect. */
    const val VPN_TUNNEL_LAST_ERROR_KEY: String = "vpn_tunnel_last_error"

    /** Latest Xray status line from NE callbacks (informational). */
    const val VPN_TUNNEL_STATUS_KEY: String = "vpn_tunnel_status_message"

    /** Process resident memory in bytes — PacketTunnel samples while running. */
    const val VPN_TUNNEL_MEMORY_BYTES_KEY: String = "vpn_tunnel_memory_bytes"
}
