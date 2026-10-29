package com.android.xrayfa.shared.navigation

import com.android.xrayfa.common.core.VpnStartupFailure
import com.android.xrayfa.datastore.ConnectMapOrigin
import com.android.xrayfa.model.Node

/** Home tab presentation state owned by [HomeComponent]. */
data class HomeState(
    val connectMapHttpExitEnabled: Boolean = false,
    val connectMapEnabled: Boolean = false,
    val connectMapOrigin: ConnectMapOrigin? = null,
    val isConnected: Boolean = false,
    val selectedNode: Node? = null,
    val busy: Boolean = false,
    val startupPending: Boolean = false,
    val startupFailure: VpnStartupFailure? = null,
    val canRestoreGeodata: Boolean = false,
    val uploadSpeedKbps: Double = 0.0,
    val downloadSpeedKbps: Double = 0.0,
    val delayMs: Long = 0L,
    val testing: Boolean = false,
)
