package com.android.xrayfa.shared.navigation

import com.android.xrayfa.datastore.SettingsRepository
import com.android.xrayfa.datastore.SettingsState
import com.android.xrayfa.datastore.Theme
import com.android.xrayfa.vpn.VpnController
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import kotlinx.coroutines.launch

class DefaultSettingsComponent(
    componentContext: ComponentContext,
    private val settingsRepository: SettingsRepository,
    private val vpnController: VpnController,
) : SettingsComponent,
    ComponentContext by componentContext {
    private val scope = coroutineScope()

    private val _state = MutableValue(SettingsState())
    override val state: Value<SettingsState> = _state

    init {
        scope.launch {
            settingsRepository.settingsFlow.collect { settings ->
                _state.value = settings
            }
        }
    }

    override fun onSetTheme(themeCode: Int) {
        scope.launch {
            settingsRepository.setDarkMode(Theme.fromCode(themeCode))
        }
    }

    override fun onSetBootAutoStart(enable: Boolean) {
        scope.launch {
            settingsRepository.setBootAutoStart(enable)
        }
    }

    override fun onSetHideFromRecents(enable: Boolean) {
        scope.launch {
            settingsRepository.setHideFromRecentsState(enable)
        }
    }

    override fun onSetLanSocksProxyEnable(enable: Boolean) {
        scope.launch {
            settingsRepository.setSocksListen(resolveSocksListenAddressForLan(enable))
            vpnController.restartIfNeeded()
        }
    }

    override fun onSetLanHttpProxyEnable(enable: Boolean) {
        scope.launch {
            settingsRepository.setLanHttpProxyEnable(enable)
            vpnController.restartIfNeeded()
        }
    }

    override fun onSetSendHwid(enable: Boolean) {
        scope.launch {
            settingsRepository.setSendHwid(enable)
        }
    }

    companion object {
        const val LOCAL_PROXY_LISTEN_ADDRESS = "127.0.0.1"
        const val LAN_PROXY_LISTEN_ADDRESS = "0.0.0.0"

        private fun resolveSocksListenAddressForLan(enable: Boolean): String =
            if (enable) LAN_PROXY_LISTEN_ADDRESS else LOCAL_PROXY_LISTEN_ADDRESS
    }
}
