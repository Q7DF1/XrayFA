package com.android.xrayfa.vpn

/** Platform-neutral VPN connection state (maps from Android boolean service status today). */
sealed interface VpnState {
    data object Disconnected : VpnState
    data object Connected : VpnState
}
