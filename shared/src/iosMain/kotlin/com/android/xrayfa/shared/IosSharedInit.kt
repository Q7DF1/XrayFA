package com.android.xrayfa.shared

import com.android.xrayfa.vpn.IosVpnController
import com.android.xrayfa.vpn.VpnState
import com.android.xrayfa.vpn.setPendingConfig
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * iOS-side helpers until Compose screens inject [VpnController] via Koin (E.6+).
 */
object IosSharedInit : KoinComponent {
    private val scope = MainScope()
    private val vpnController: IosVpnController by inject()

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
