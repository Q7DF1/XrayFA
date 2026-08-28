package com.android.xrayfa.vpn

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Android library stub — real implementation lives in `:androidApp` as [com.android.xrayfa.core.AppVpnController]
 * to avoid a platform → app dependency cycle.
 */
internal class AndroidVpnControllerStub : VpnController {
    private val _state = MutableStateFlow(VpnState.Disconnected)
    override val state: StateFlow<VpnState> = _state.asStateFlow()

    private val _connectError = MutableStateFlow<String?>(null)
    override val connectError: StateFlow<String?> = _connectError.asStateFlow()

    override suspend fun connect(): Boolean = false

    override fun disconnect() = Unit

    override suspend fun restartIfNeeded() = Unit
}
