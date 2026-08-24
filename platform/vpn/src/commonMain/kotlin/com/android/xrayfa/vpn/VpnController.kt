package com.android.xrayfa.vpn

import kotlinx.coroutines.flow.StateFlow

/**
 * Cross-platform VPN control surface.
 *
 * Android actual: [com.android.xrayfa.core.AppVpnController] delegates to [com.android.xrayfa.core.XrayBaseServiceManager].
 * iOS actual: stub until Network Extension wiring.
 */
interface VpnController {
    val state: StateFlow<VpnState>

    /** Non-null after the most recent failed [connect] on platforms that surface tunnel errors. */
    val connectError: StateFlow<String?>

    /** Start VPN with the currently selected node configuration. */
    suspend fun connect(): Boolean

    fun disconnect()

    /** Restart when already connected (e.g. after settings change). */
    suspend fun restartIfNeeded()
}
