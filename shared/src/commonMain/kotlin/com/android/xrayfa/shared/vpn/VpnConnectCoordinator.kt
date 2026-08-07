package com.android.xrayfa.shared.vpn

/**
 * Prepares platform-specific VPN config (App Group / service options) before [connect].
 */
interface VpnConnectCoordinator {
    suspend fun prepareConfigForConnect(): Boolean

    suspend fun connect(): Boolean

    fun disconnect()
}
