package com.android.xrayfa.shared.navigation

import com.android.xrayfa.common.core.DelayMeasurement
import com.android.xrayfa.common.core.DelayProbe
import com.android.xrayfa.common.core.XrayCore
import com.android.xrayfa.common.core.StartupFailureKind
import com.android.xrayfa.common.core.StartupStage
import com.android.xrayfa.common.core.VpnStartupFailure
import com.android.xrayfa.common.core.classifyStartupFailure
import com.android.xrayfa.common.core.homeDelayTestEnabled
import com.android.xrayfa.datastore.SettingsRepository
import com.android.xrayfa.parser.ParserFactory
import com.android.xrayfa.model.isJsonConfig
import com.android.xrayfa.repository.NodeRepository
import com.android.xrayfa.shared.vpn.EmptyTrafficStatsSource
import com.android.xrayfa.shared.vpn.TrafficStatsSource
import com.android.xrayfa.shared.vpn.VpnConnectCoordinator
import com.android.xrayfa.shared.vpn.createDelayProbe
import com.android.xrayfa.vpn.VpnController
import com.android.xrayfa.vpn.isConnected
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.update
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class DefaultHomeComponent(
    componentContext: ComponentContext,
    private val vpnController: VpnController,
    private val nodeRepository: NodeRepository,
    private val coordinator: VpnConnectCoordinator,
    private val trafficStatsSource: TrafficStatsSource = EmptyTrafficStatsSource,
    private val settingsRepository: SettingsRepository,
    xrayCore: XrayCore,
    parserFactory: ParserFactory,
) : HomeComponent,
    ComponentContext by componentContext {
    private val scope = coroutineScope()
    private val delayProbe: DelayProbe = createDelayProbe(xrayCore, parserFactory)

    private val _state = MutableValue(HomeState(canRestoreGeodata = vpnController.canRestoreGeodata))
    override val state: Value<HomeState> = _state

    init {
        scope.launch {
            vpnController.state.collect { vpnState ->
                _state.update { it.copy(isConnected = vpnState.isConnected) }
            }
        }
        scope.launch {
            vpnController.startupState.collect { startup ->
                _state.update { current -> current.copy(
                    startupPending = startup.pending,
                    startupFailure = if (startup.pending) current.startupFailure ?: startup.failure else startup.failure,
                ) }
            }
        }
        scope.launch {
            // iOS and other controllers may only expose the legacy error string.
            vpnController.connectError.collect { message ->
                if (message != null && vpnController.startupState.value.attemptId == 0L && vpnController.startupState.value.failure == null) {
                    _state.update { it.copy(startupFailure = classifyStartupFailure(
                        IllegalStateException(message), StartupStage.VPN)) }
                }
            }
        }
        scope.launch {
            nodeRepository.querySelectedNode().collect { node ->
                _state.update { it.copy(selectedNode = node) }
            }
        }
        scope.launch {
            trafficStatsSource.speedsKbps.collect { (up, down) ->
                _state.update { it.copy(uploadSpeedKbps = up, downloadSpeedKbps = down) }
            }
        }
    }

    override fun onConnectToggle() {
        val current = _state.value
        if (current.busy || current.startupPending) return
        if (current.isConnected) {
            coordinator.disconnect()
            return
        }
        if (current.selectedNode == null) {
            _state.update { it.copy(startupFailure = VpnStartupFailure(StartupFailureKind.NO_NODE,
                StartupStage.PREPARATION, "No VPN configuration is selected")) }
            return
        }

        _state.update { it.copy(busy = true) }
        scope.launch {
            try {
                val prepared = coordinator.prepareConfigForConnect()
                if (!prepared) {
                    _state.update { it.copy(startupFailure = VpnStartupFailure(
                        if (current.selectedNode.isJsonConfig) StartupFailureKind.INVALID_JSON else StartupFailureKind.NO_NODE,
                        StartupStage.PREPARATION, "The selected configuration could not be prepared")) }
                    return@launch
                }
                val connected = coordinator.connect()
                if (!connected) {
                    _state.update { it.copy(startupFailure = vpnController.startupState.value.failure
                        ?: classifyStartupFailure(IllegalStateException(vpnController.connectError.value
                            ?: "VPN startup did not complete"), StartupStage.VPN)) }
                } else {
                    _state.update { it.copy(startupFailure = null) }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.update { it.copy(startupFailure = classifyStartupFailure(e, StartupStage.CONFIGURATION)) }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    override fun onVpnPermissionDenied() {
        _state.update { it.copy(startupFailure = VpnStartupFailure(StartupFailureKind.VPN_PERMISSION,
            StartupStage.VPN, "Android VPN authorization was declined or cancelled")) }
    }

    override fun onRetryStartup() {
        if (!_state.value.isConnected) {
            onConnectToggle()
            return
        }
        if (_state.value.busy || _state.value.startupPending) return
        _state.update { it.copy(busy = true) }
        scope.launch {
            try {
                vpnController.restartIfNeeded()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.update { it.copy(startupFailure = classifyStartupFailure(e, StartupStage.CONFIGURATION)) }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    override fun onRestoreGeodata() {
        if (_state.value.busy || _state.value.startupPending || !vpnController.canRestoreGeodata) return
        _state.update { it.copy(busy = true) }
        scope.launch {
            var restored = false
            try {
                restored = vpnController.restoreGeodata()
                if (!restored) _state.update { it.copy(startupFailure = vpnController.startupState.value.failure
                    ?: VpnStartupFailure(StartupFailureKind.GEODATA_INVALID, StartupStage.GEODATA,
                        "Bundled geodata could not be restored")) }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.update { it.copy(startupFailure = classifyStartupFailure(e, StartupStage.GEODATA)) }
            } finally {
                _state.update { it.copy(busy = false) }
            }
            if (restored) onRetryStartup()
        }
    }

    override fun onTestDelay() {
        val snapshot = _state.value
        if (snapshot.selectedNode?.isJsonConfig == true) return
        if (!homeDelayTestEnabled(snapshot.isConnected, snapshot.testing)) return
        scope.launch {
            _state.update { it.copy(testing = true, delayMs = DelayMeasurement.TESTING_SENTINEL) }
            val testUrl = settingsRepository.settingsFlow.first().delayTestUrl
            val result =
                withTimeoutOrNull(DelayMeasurement.HOME_TIMEOUT_MS) {
                    withContext(Dispatchers.Default) {
                        delayProbe.measureHome(testUrl, snapshot.selectedNode?.url)
                    }
                } ?: DelayMeasurement.TIMEOUT_SENTINEL
            _state.update { it.copy(testing = false, delayMs = result) }
        }
    }

}
