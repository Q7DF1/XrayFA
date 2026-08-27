package com.android.xrayfa.ui.navigation

import com.android.xrayfa.agent.AgentScreen

/** External intents / shortcuts routed into [com.android.xrayfa.ui.AndroidAppShell]. */
sealed interface AndroidRootAction {
    data object OpenQrScan : AndroidRootAction

    data object ConnectVpn : AndroidRootAction

    data object DisconnectVpn : AndroidRootAction

    data class OpenScreen(val screen: AgentScreen) : AndroidRootAction
}
