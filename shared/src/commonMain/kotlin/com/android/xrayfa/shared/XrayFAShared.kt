package com.android.xrayfa.shared

import com.android.xrayfa.vpn.VpnController
import com.android.xrayfa.vpn.VpnState

/**
 * Umbrella entry for the iOS-exported [XrayFAShared] framework.
 * Xcode / Swift links this module to access domain, core, and platform layers.
 */
object XrayFAShared {
    /** Library version marker for iOS shell integration smoke tests. */
    const val VERSION: String = "0.1.0-kmp"

    /** Exposes VPN state types to framework consumers without reaching into packages. */
    fun vpnDisconnected(): VpnState = VpnState.Disconnected
}

/** Factory hook for iOS app to supply [VpnController] after Koin/DI wiring (E.5b+). */
typealias VpnControllerProvider = () -> VpnController
