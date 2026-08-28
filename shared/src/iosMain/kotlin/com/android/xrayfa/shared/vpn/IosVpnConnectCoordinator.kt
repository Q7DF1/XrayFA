package com.android.xrayfa.shared.vpn

import com.android.xrayfa.parser.ParserFactory
import com.android.xrayfa.vpn.IosVpnController
import com.android.xrayfa.vpn.setPendingConfig
import kotlinx.coroutines.flow.first

class IosVpnConnectCoordinator(
    private val vpnController: IosVpnController,
    private val parserFactory: ParserFactory,
    private val startOptionsResolver: VpnStartOptionsResolver,
) : VpnConnectCoordinator {
    override suspend fun prepareConfigForConnect(): Boolean {
        val startOptions = startOptionsResolver.resolve() ?: return false
        val configJson = parserFactory.getParser(startOptions.url).parse(startOptions)
        vpnController.setPendingConfig(configJson)
        return true
    }

    override suspend fun connect(): Boolean = vpnController.connect()

    override fun disconnect() {
        vpnController.disconnect()
    }
}
