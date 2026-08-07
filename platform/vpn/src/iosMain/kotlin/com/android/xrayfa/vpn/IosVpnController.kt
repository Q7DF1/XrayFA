package com.android.xrayfa.vpn

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** iOS compile-only stub until NEPacketTunnelProvider integration. */
class IosVpnController : VpnController {
    private val _state = MutableStateFlow<VpnState>(VpnState.Disconnected)
    override val state: StateFlow<VpnState> = _state.asStateFlow()

    override suspend fun connect(): Boolean = false

    override fun disconnect() {
        _state.value = VpnState.Disconnected
    }

    override suspend fun restartIfNeeded() = Unit
}
