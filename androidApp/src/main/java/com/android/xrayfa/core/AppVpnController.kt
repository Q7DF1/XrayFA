package com.android.xrayfa.core

import com.android.xrayfa.vpn.VpnController
import com.android.xrayfa.vpn.VpnState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import com.android.xrayfa.common.core.*

/**
 * Android [VpnController] backed by existing [XrayBaseServiceManager] / [XrayBaseService.statusFlow].
 * ViewModels may migrate to this interface incrementally; behaviour matches the manager today.
 */
class AppVpnController(
    private val manager: XrayBaseServiceManager,
    scope: CoroutineScope,
    private val startupStateStore: VpnStartupStateStore,
    private val assetInstaller: XrayAssetInstaller,
) : VpnController {

    private val _state = MutableStateFlow(
        if (XrayBaseService.statusFlow.value) VpnState.Connected else VpnState.Disconnected,
    )
    override val state: StateFlow<VpnState> = _state.asStateFlow()

    private val _connectError = MutableStateFlow<String?>(null)
    override val connectError: StateFlow<String?> = _connectError.asStateFlow()
    override val startupState = startupStateStore.state
    override val canRestoreGeodata = true

    init {
        scope.launch {
            XrayBaseService.statusFlow.collect { running ->
                _state.value = if (running) VpnState.Connected else VpnState.Disconnected
            }
        }
        scope.launch {
            startupState.collect { _connectError.value = it.failure?.technicalDetails }
        }
    }

    override suspend fun connect(): Boolean {
        if (!manager.startXrayBaseService()) return false
        val attemptId = startupState.value.attemptId
        val result = withTimeoutOrNull(30_000) {
            startupState.first { it.attemptId != attemptId || !it.pending }
        }
        if (result == null) {
            startupStateStore.fail(VpnStartupFailure(StartupFailureKind.CORE_INITIALIZATION,
                StartupStage.CORE, "VPN startup did not complete within 30 seconds"), attemptId)
            manager.stopXrayBaseService()
            return false
        }
        return result.attemptId == attemptId && result.failure == null && XrayBaseService.statusFlow.value
    }

    override suspend fun restoreGeodata(): Boolean {
        return try {
            withContext(Dispatchers.IO) { assetInstaller.ensureInstalled(forceRestore = true) }
            true
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            startupStateStore.fail(classifyStartupFailure(e, StartupStage.GEODATA))
            false
        }
    }

    override fun disconnect() = manager.stopXrayBaseService()

    override suspend fun restartIfNeeded() = manager.restartXrayBaseServiceIfNeed()
}
