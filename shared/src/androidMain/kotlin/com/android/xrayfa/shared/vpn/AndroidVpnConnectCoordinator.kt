package com.android.xrayfa.shared.vpn

/** Android host app still owns VPN orchestration via [com.android.xrayfa.viewmodel.XrayViewmodel]. */
class AndroidVpnConnectCoordinator : VpnConnectCoordinator {
    override suspend fun prepareConfigForConnect(): Boolean = false

    override suspend fun connect(): Boolean = false

    override fun disconnect() = Unit
}
