package com.android.xrayfa.shared.navigation

import com.android.xrayfa.common.core.GeoLiteInstaller
import com.android.xrayfa.common.core.XrayAssetPaths
import com.android.xrayfa.common.core.geoLiteDownloadEnabled
import com.android.xrayfa.common.routing.DomainStrategy
import com.android.xrayfa.common.routing.RoutingMode
import com.android.xrayfa.common.routing.Rule
import com.android.xrayfa.datastore.SettingsRepository
import com.android.xrayfa.datastore.SettingsState
import com.android.xrayfa.datastore.Theme
import com.android.xrayfa.network.FileDownloader
import com.android.xrayfa.vpn.VpnController
import com.android.xrayfa.vpn.isConnected
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class DefaultSettingsComponent(
    componentContext: ComponentContext,
    private val settingsRepository: SettingsRepository,
    private val vpnController: VpnController,
    private val fileDownloader: FileDownloader,
    assetPaths: XrayAssetPaths,
) : SettingsComponent,
    ComponentContext by componentContext {
    private val scope = coroutineScope()
    private val geoLiteInstaller =
        GeoLiteInstaller(
            destPath = assetPaths.geoLiteDatabasePath,
            download = { url, dest, onProgress ->
                fileDownloader.downloadToFile(url, dest, onProgress)
            },
            setInstalled = settingsRepository::setGeoLiteInstall,
        )

    private val _state = MutableValue(SettingsState())
    override val state: Value<SettingsState> = _state

    private val _geoLiteDownload =
        MutableValue(
            GeoLiteDownloadState(vpnConnected = vpnController.state.value.isConnected),
        )
    override val geoLiteDownload: Value<GeoLiteDownloadState> = _geoLiteDownload

    init {
        scope.launch {
            combine(
                settingsRepository.settingsFlow,
                settingsRepository.packagesFlow,
            ) { settings, packages ->
                settings.copy(allowedPackages = packages)
            }.collect { settings ->
                _state.value = settings
            }
        }
        scope.launch {
            vpnController.state.collect { vpn ->
                _geoLiteDownload.value =
                    _geoLiteDownload.value.copy(vpnConnected = vpn.isConnected)
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

    override fun onSetAgentFunctionsEnabled(enable: Boolean) {
        scope.launch {
            settingsRepository.setAgentFunctionsEnabled(enable)
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

    override fun onSetSocksPort(port: Int) {
        scope.launch {
            settingsRepository.setSocksPort(port)
            vpnController.restartIfNeeded()
        }
    }

    override fun onSetHttpPort(port: Int) {
        scope.launch {
            settingsRepository.setHttpPort(port)
            vpnController.restartIfNeeded()
        }
    }

    override fun onSetSocksUsername(username: String) {
        scope.launch {
            settingsRepository.setSocksUsername(username)
            vpnController.restartIfNeeded()
        }
    }

    override fun onSetSocksPassword(password: String) {
        scope.launch {
            settingsRepository.setSocksPassword(password)
            vpnController.restartIfNeeded()
        }
    }

    override fun onSetDnsIPv4(dns: String) {
        scope.launch {
            settingsRepository.setDnsIPv4(dns)
            vpnController.restartIfNeeded()
        }
    }

    override fun onSetDnsIPv6(dns: String) {
        scope.launch {
            settingsRepository.setDnsIPv6(dns)
            vpnController.restartIfNeeded()
        }
    }

    override fun onSetIpV6Enable(enable: Boolean) {
        scope.launch {
            settingsRepository.setIpV6Enable(enable)
        }
    }

    override fun onSetRoutingMode(mode: RoutingMode) {
        scope.launch {
            settingsRepository.setRoutingMode(mode)
            vpnController.restartIfNeeded()
        }
    }

    override fun onSetDomainStrategy(strategy: DomainStrategy) {
        scope.launch {
            settingsRepository.setDomainStrategy(strategy)
            vpnController.restartIfNeeded()
        }
    }

    override fun onSetRoutingRules(rules: List<Rule>) {
        scope.launch {
            settingsRepository.setRoutingRules(rules)
            vpnController.restartIfNeeded()
        }
    }

    override fun onDownloadGeoLite() {
        val snapshot = _geoLiteDownload.value
        if (!geoLiteDownloadEnabled(snapshot.vpnConnected, snapshot.downloading)) return
        scope.launch {
            _geoLiteDownload.value = snapshot.copy(downloading = true, progress = 0f)
            try {
                geoLiteInstaller.install { progress ->
                    _geoLiteDownload.value = _geoLiteDownload.value.copy(progress = progress)
                }
            } finally {
                _geoLiteDownload.value =
                    _geoLiteDownload.value.copy(downloading = false, progress = 0f)
            }
        }
    }

    companion object {
        const val LOCAL_PROXY_LISTEN_ADDRESS = "127.0.0.1"
        const val LAN_PROXY_LISTEN_ADDRESS = "0.0.0.0"

        private fun resolveSocksListenAddressForLan(enable: Boolean): String =
            if (enable) LAN_PROXY_LISTEN_ADDRESS else LOCAL_PROXY_LISTEN_ADDRESS
    }
}
