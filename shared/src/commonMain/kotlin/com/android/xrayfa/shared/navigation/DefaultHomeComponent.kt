package com.android.xrayfa.shared.navigation

import com.android.xrayfa.repository.NodeRepository
import com.android.xrayfa.shared.vpn.EmptyTrafficStatsSource
import com.android.xrayfa.shared.vpn.TrafficStatsSource
import com.android.xrayfa.shared.vpn.VpnConnectCoordinator
import com.android.xrayfa.vpn.VpnController
import com.android.xrayfa.vpn.isConnected
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.update
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class DefaultHomeComponent(
    componentContext: ComponentContext,
    private val vpnController: VpnController,
    private val nodeRepository: NodeRepository,
    private val coordinator: VpnConnectCoordinator,
    private val trafficStatsSource: TrafficStatsSource = EmptyTrafficStatsSource,
) : HomeComponent,
    ComponentContext by componentContext {
    private val scope = coroutineScope()

    private val _state = MutableValue(HomeState())
    override val state: Value<HomeState> = _state

    init {
        scope.launch {
            vpnController.state.collect { vpnState ->
                _state.update { it.copy(isConnected = vpnState.isConnected) }
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
        if (current.selectedNode == null) {
            scope.launch {
                _state.update { it.copy(showConfigError = true) }
                delay(CONFIG_ERROR_VISIBLE_MS)
                _state.update { it.copy(showConfigError = false) }
            }
            return
        }

        if (current.isConnected) {
            coordinator.disconnect()
            return
        }

        _state.update { it.copy(busy = true, connectionErrorMessage = null) }
        scope.launch {
            try {
                val prepared = coordinator.prepareConfigForConnect()
                if (!prepared) return@launch
                val connected = coordinator.connect()
                if (!connected) {
                    _state.update {
                        it.copy(connectionErrorMessage = vpnController.connectError.value)
                    }
                    delay(CONNECTION_ERROR_VISIBLE_MS)
                    _state.update { it.copy(connectionErrorMessage = null) }
                }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    private companion object {
        const val CONFIG_ERROR_VISIBLE_MS = 2_000L
        const val CONNECTION_ERROR_VISIBLE_MS = 4_000L
    }
}
