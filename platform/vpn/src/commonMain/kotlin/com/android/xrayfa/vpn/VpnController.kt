package com.android.xrayfa.vpn

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.android.xrayfa.common.core.VpnStartupState

private val emptyStartupState = MutableStateFlow(VpnStartupState()).asStateFlow()

/**
 * Cross-platform VPN control surface.
 *
 * Android actual: [com.android.xrayfa.core.AppVpnController] delegates to [com.android.xrayfa.core.XrayBaseServiceManager].
 * iOS actual: stub until Network Extension wiring.
 */
interface VpnController {
    /** Invalidates a persisted start snapshot when its source record is deleted. */
    fun clearPendingConfig() = Unit
    val state: StateFlow<VpnState>

    /** Non-null after the most recent failed [connect] on platforms that surface tunnel errors. */
    val connectError: StateFlow<String?>
    val startupState: StateFlow<VpnStartupState> get() = emptyStartupState
    val canRestoreGeodata: Boolean get() = false
    suspend fun restoreGeodata(): Boolean = false

    /** Start VPN with the currently selected node configuration. */
    suspend fun connect(): Boolean

    fun disconnect()

    /** Restart when already connected (e.g. after settings change). */
    suspend fun restartIfNeeded()
}
