package com.android.xrayfa.shared.vpn

import com.android.xrayfa.parser.ParserFactory
import com.android.xrayfa.config.*
import kotlinx.serialization.json.*
import com.android.xrayfa.vpn.IosVpnController
import com.android.xrayfa.vpn.setPendingConfig
import kotlinx.coroutines.flow.first

class IosVpnConnectCoordinator(
    private val vpnController: IosVpnController,
    private val parserFactory: ParserFactory,
    private val startOptionsResolver: VpnStartOptionsResolver,
) : VpnConnectCoordinator {
    init { vpnController.prepareForRestart = { prepareConfigForConnect() } }

    override suspend fun prepareConfigForConnect(): Boolean {
        val startOptions = startOptionsResolver.resolve() ?: run {
            vpnController.setPendingConfig("")
            return false
        }
        val jsonConfig = startOptions.jsonConfig
        if (jsonConfig != null) {
            val runtime = JsonVpnConfig.prepare(jsonConfig, JsonVpnTransport(), startOptions.jsonInboundTag)
            // Single value: the extension cannot observe JSON and channel metadata from different saves.
            vpnController.setPendingConfig(buildJsonObject {
                put("_xrayfaEnvelopeVersion", 1)
                put("config", runtime)
                put("socksPort", 10808)
                put("dns", JsonVpnConfig.VPN_DNS)
            }.toString())
            return true
        }
        val configJson = parserFactory.getParser(startOptions.url).parse(startOptions)
        vpnController.setPendingConfig(configJson)
        return true
    }

    override suspend fun connect(): Boolean = vpnController.connect()

    override fun disconnect() {
        vpnController.disconnect()
    }
}
