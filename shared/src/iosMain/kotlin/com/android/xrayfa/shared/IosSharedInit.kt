package com.android.xrayfa.shared

import com.android.xrayfa.vpn.IosVpnController
import com.android.xrayfa.vpn.VpnState
import com.android.xrayfa.vpn.setPendingConfig
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * iOS-side entry until Compose Multiplatform [MainViewController] lands (E.6).
 * Exposes [IosVpnController] to Swift for VPN smoke tests (E.5e).
 */
object IosSharedInit {
    private val scope = MainScope()
    private val vpnController = IosVpnController(scope)

    fun platformName(): String = "ios"

    fun vpnState(): StateFlow<VpnState> = vpnController.state

    fun setPendingVpnConfig(configJson: String) {
        vpnController.setPendingConfig(configJson)
    }

    fun connectVpn(onResult: (Boolean) -> Unit) {
        scope.launch {
            onResult(vpnController.connect())
        }
    }

    fun disconnectVpn() {
        vpnController.disconnect()
    }
}
