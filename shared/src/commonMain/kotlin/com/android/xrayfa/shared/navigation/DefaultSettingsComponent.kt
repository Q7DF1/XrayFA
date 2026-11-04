package com.android.xrayfa.shared.navigation

import com.android.xrayfa.common.core.AssetFiles
import com.android.xrayfa.common.core.CityDatabaseInstaller
import com.android.xrayfa.common.core.GeoLiteInstaller
import com.android.xrayfa.common.core.MAX_MMDB_BYTES
import com.android.xrayfa.common.core.MmdbCityLookup
import com.android.xrayfa.common.core.XrayAssetPaths
import com.android.xrayfa.common.core.geoLiteDownloadEnabled
import com.android.xrayfa.common.core.geoLiteDownloadSupported
import com.android.xrayfa.common.routing.DomainStrategy
import com.android.xrayfa.common.routing.RoutingMode
import com.android.xrayfa.common.routing.Rule
import com.android.xrayfa.datastore.ConnectMapOrigin
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DefaultSettingsComponent(
    componentContext: ComponentContext,
    private val settingsRepository: SettingsRepository,
    private val vpnController: VpnController,
    private val fileDownloader: FileDownloader,
    private val assetPaths: XrayAssetPaths,
) : SettingsComponent,
    ComponentContext by componentContext {
    private val scope = coroutineScope()
    private var geoLiteDownloadJob: Job? = null
    private val geoLiteInstaller =
        GeoLiteInstaller(
            destPath = assetPaths.geoLiteDatabasePath,
            download = { url, dest, onProgress ->
                fileDownloader.downloadToFile(url, dest, maxBytes = MAX_MMDB_BYTES, onProgress = onProgress)
            },
            setInstalled = settingsRepository::setGeoLiteInstall,
        )

    private var cityDownloadJob: Job? = null
    private val cityInstaller = CityDatabaseInstaller(
        destination = assetPaths.cityDatabasePath,
        download = { url, path, progress ->
            fileDownloader.downloadToFile(url, path, maxBytes = MAX_MMDB_BYTES, onProgress = progress)
        },
    )
    private val _cityDatabase = MutableValue(CityDatabaseState())
    override val cityDatabase: Value<CityDatabaseState> = _cityDatabase

    private val _state = MutableValue(SettingsState())
    override val state: Value<SettingsState> = _state

    private val _geoLiteDownload =
        MutableValue(
            GeoLiteDownloadState(
                vpnConnected = vpnController.state.value.isConnected,
                downloadSupported = geoLiteDownloadSupported,
            ),
        )
    override val geoLiteDownload: Value<GeoLiteDownloadState> = _geoLiteDownload

    init {
        // A process killed during transfer cannot run finally; remove its partial file on restart.
        runCatching { geoLiteInstaller.clearPartialDownload() }
        runCatching { cityInstaller.clearPartialDownload() }.onFailure {
            _cityDatabase.value = _cityDatabase.value.copy(failed = true)
        }
        scope.launch {
            val installed = withContext(Dispatchers.Default) {
                runCatching { AssetFiles.read(assetPaths.cityDatabasePath)?.let(::MmdbCityLookup) != null }
                    .getOrDefault(false)
            }
            _cityDatabase.value = _cityDatabase.value.copy(installed = installed)
            settingsRepository.disableConnectMapIfUnavailable(
                cityDatabaseInstalled = installed,
                httpExitSupported = geoLiteDownloadSupported,
            )
        }
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

    override fun onSetHomeScreenWidgetsEnabled(enable: Boolean) {
        scope.launch { settingsRepository.setHomeScreenWidgetsEnabled(enable) }
    }

    override fun onSetConnectMapOrigin(origin: ConnectMapOrigin?) {
        scope.launch { settingsRepository.setConnectMapOrigin(origin) }
    }

    override fun onSetConnectMapHttpExitEnabled(enable: Boolean) {
        scope.launch {
            settingsRepository.setConnectMapHttpExitEnabled(
                enable = enable && geoLiteDownloadSupported,
                cityDatabaseInstalled = _cityDatabase.value.installed,
            )
        }
    }

    override fun onSetConnectMapEnabled(enable: Boolean) {
        scope.launch {
            settingsRepository.setConnectMapEnabled(
                enable = enable,
                cityDatabaseInstalled = _cityDatabase.value.installed,
                httpExitSupported = geoLiteDownloadSupported,
            )
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

    override fun onEnableDeveloperMode() {
        scope.launch { settingsRepository.setDeveloperModeEnabled(true) }
    }

    override fun onExitDeveloperMode() {
        scope.launch {
            val wasUsingUdpDns =
                settingsRepository.settingsFlow.first().let { it.developerModeEnabled && !it.dnsOverHttpsEnabled }
            settingsRepository.setDeveloperModeEnabled(false)
            if (wasUsingUdpDns) vpnController.restartIfNeeded()
        }
    }

    override fun onSetDnsOverHttpsEnabled(enable: Boolean) {
        scope.launch {
            if (!settingsRepository.settingsFlow.first().developerModeEnabled) return@launch
            settingsRepository.setDnsOverHttpsEnabled(enable)
            vpnController.restartIfNeeded()
        }
    }

    override fun onSetDnsOverHttpsServers(servers: String) {
        scope.launch {
            settingsRepository.setDnsOverHttpsServers(servers)
            vpnController.restartIfNeeded()
        }
    }

    override fun onSetUdpDnsServers(servers: String) {
        scope.launch {
            if (!settingsRepository.settingsFlow.first().developerModeEnabled) return@launch
            settingsRepository.setUdpDnsServers(servers)
            vpnController.restartIfNeeded()
        }
    }

    override fun onSetIpV6Enable(enable: Boolean) {
        scope.launch {
            settingsRepository.setIpV6Enable(enable)
        }
    }

    override fun onSetDelayTestUrl(url: String) {
        scope.launch {
            settingsRepository.setDelayTestUrl(url)
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

    override fun onInitializeRussiaRoutingRecommendation(language: String) {
        scope.launch { settingsRepository.initializeRussiaRoutingRecommendation(language) }
    }

    override fun onResolveRussiaRoutingRecommendation(accept: Boolean) {
        scope.launch {
            settingsRepository.resolveRussiaRoutingRecommendation(accept)
            if (accept) vpnController.restartIfNeeded()
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
        if (!geoLiteDownloadEnabled(
                snapshot.vpnConnected,
                snapshot.downloading,
                snapshot.downloadSupported,
            )
        ) {
            return
        }
        _geoLiteDownload.value = snapshot.copy(downloading = true, progress = 0f)
        val job = scope.launch(start = CoroutineStart.LAZY) {
            geoLiteInstaller.install { progress ->
                _geoLiteDownload.value = _geoLiteDownload.value.copy(progress = progress)
            }
        }
        geoLiteDownloadJob = job
        job.invokeOnCompletion {
            if (geoLiteDownloadJob === job) {
                geoLiteDownloadJob = null
                _geoLiteDownload.value = _geoLiteDownload.value.copy(downloading = false, progress = 0f)
            }
        }
        job.start()
    }

    override fun onCancelGeoLiteDownload() {
        geoLiteDownloadJob?.cancel()
    }

    override fun onDownloadCityDatabase() {
        if (_cityDatabase.value.downloading || !geoLiteDownloadSupported ||
            !vpnController.state.value.isConnected
        ) return
        _cityDatabase.value = _cityDatabase.value.copy(downloading = true, progress = 0f, failed = false)
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                cityInstaller.install { progress ->
                    _cityDatabase.value = _cityDatabase.value.copy(progress = progress)
                }
                _cityDatabase.value = _cityDatabase.value.copy(installed = true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _cityDatabase.value = _cityDatabase.value.copy(failed = true)
            }
        }
        cityDownloadJob = job
        job.invokeOnCompletion {
            // Also handles cancellation before a scheduled coroutine starts. For a started
            // transfer, the installer has closed and removed the partial file by this point.
            if (cityDownloadJob === job) {
                cityDownloadJob = null
                _cityDatabase.value = _cityDatabase.value.copy(downloading = false, progress = 0f)
            }
        }
        job.start()
    }

    override fun onCancelCityDatabaseDownload() {
        cityDownloadJob?.cancel()
    }

    companion object {
        const val LOCAL_PROXY_LISTEN_ADDRESS = "127.0.0.1"
        const val LAN_PROXY_LISTEN_ADDRESS = "0.0.0.0"

        private fun resolveSocksListenAddressForLan(enable: Boolean): String =
            if (enable) LAN_PROXY_LISTEN_ADDRESS else LOCAL_PROXY_LISTEN_ADDRESS
    }
}
