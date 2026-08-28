package com.android.xrayfa.shared.vpn

import com.android.xrayfa.vpn.VpnController

/**
 * Android VPN connect orchestration via [VpnController] and [VpnStartOptionsResolver].
 * Config is resolved at connect time inside [com.android.xrayfa.core.XrayBaseServiceManager].
 */
class AndroidVpnConnectCoordinator(
    private val vpnController: VpnController,
    private val startOptionsResolver: VpnStartOptionsResolver,
) : VpnConnectCoordinator {
    override suspend fun prepareConfigForConnect(): Boolean = startOptionsResolver.resolve() != null

    override suspend fun connect(): Boolean = vpnController.connect()

    override fun disconnect() = vpnController.disconnect()
}
