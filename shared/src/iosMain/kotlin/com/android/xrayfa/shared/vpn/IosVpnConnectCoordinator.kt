package com.android.xrayfa.shared.vpn

import com.android.xrayfa.vpn.IosVpnController
import com.android.xrayfa.vpn.setPendingConfig

class IosVpnConnectCoordinator(
    private val vpnController: IosVpnController,
) : VpnConnectCoordinator {
    override suspend fun prepareConfigForConnect(): Boolean {
        vpnController.setPendingConfig(TRIAL_CONFIG_JSON)
        return true
    }

    override suspend fun connect(): Boolean = vpnController.connect()

    override fun disconnect() {
        vpnController.disconnect()
    }

    private companion object {
        // Placeholder until parser + selected node wiring (E.6c+).
        val TRIAL_CONFIG_JSON =
            """
            {"log":{"loglevel":"warning"},"inbounds":[{"port":10808,"protocol":"socks","listen":"127.0.0.1","settings":{"udp":true}}],"outbounds":[{"protocol":"freedom","tag":"direct"}]}
            """.trimIndent()
    }
}
