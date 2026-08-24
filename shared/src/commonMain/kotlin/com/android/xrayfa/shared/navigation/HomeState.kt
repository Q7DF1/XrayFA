package com.android.xrayfa.shared.navigation

import com.android.xrayfa.model.Node

/** Home tab presentation state owned by [HomeComponent]. */
data class HomeState(
    val isConnected: Boolean = false,
    val selectedNode: Node? = null,
    val busy: Boolean = false,
    val showConfigError: Boolean = false,
    val connectionErrorMessage: String? = null,
    val uploadSpeedKbps: Double = 0.0,
    val downloadSpeedKbps: Double = 0.0,
)
