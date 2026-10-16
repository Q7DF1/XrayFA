package com.android.xrayfa.widget

import com.android.xrayfa.common.core.VpnStartupState

enum class ConnectionWidgetState { EMPTY, CONNECTING, CONNECTED, FAILED, DISCONNECTED }

fun connectionWidgetState(hasNode: Boolean, running: Boolean, startup: VpnStartupState): ConnectionWidgetState =
    when {
        startup.pending -> ConnectionWidgetState.CONNECTING
        running -> ConnectionWidgetState.CONNECTED
        !hasNode -> ConnectionWidgetState.EMPTY
        startup.failure != null -> ConnectionWidgetState.FAILED
        else -> ConnectionWidgetState.DISCONNECTED
    }
