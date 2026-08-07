package com.android.xrayfa.core

import com.android.xrayfa.vpn.VpnController
import com.android.xrayfa.vpn.VpnState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Android [VpnController] backed by existing [XrayBaseServiceManager] / [XrayBaseService.statusFlow].
 * ViewModels may migrate to this interface incrementally; behaviour matches the manager today.
 */
class AppVpnController(
    private val manager: XrayBaseServiceManager,
    scope: CoroutineScope,
) : VpnController {

    private val _state = MutableStateFlow(
        if (XrayBaseService.statusFlow.value) VpnState.Connected else VpnState.Disconnected,
    )
    override val state: StateFlow<VpnState> = _state.asStateFlow()

    init {
        scope.launch {
            XrayBaseService.statusFlow.collect { running ->
                _state.value = if (running) VpnState.Connected else VpnState.Disconnected
            }
        }
    }

    override suspend fun connect(): Boolean = manager.startXrayBaseService()

    override fun disconnect() = manager.stopXrayBaseService()

    override suspend fun restartIfNeeded() = manager.restartXrayBaseServiceIfNeed()
}
