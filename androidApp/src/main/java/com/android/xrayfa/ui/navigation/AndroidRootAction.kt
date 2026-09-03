package com.android.xrayfa.ui.navigation

import com.android.xrayfa.agent.AgentScreen

/** External intents / shortcuts routed into [com.android.xrayfa.ui.AndroidAppShell]. */
sealed interface AndroidRootAction {
    data object OpenQrScan : AndroidRootAction

    data object ConnectVpn : AndroidRootAction

    data object DisconnectVpn : AndroidRootAction

    data class OpenScreen(val screen: AgentScreen) : AndroidRootAction
}

/** Legacy Navigation3 mapping; production routing uses [com.android.xrayfa.shared.navigation.toRootNavigation]. */
fun AgentScreen.toDestination(): NavigateDestination =
    when (this) {
        AgentScreen.Home -> Home
        AgentScreen.Config -> Config
        AgentScreen.Subscriptions -> Subscription
        AgentScreen.Settings -> Settings
        AgentScreen.Apps -> Apps
        AgentScreen.RouteSettings -> RouteSettings
    }
