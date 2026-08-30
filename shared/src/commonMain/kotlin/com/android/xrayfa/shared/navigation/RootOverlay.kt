package com.android.xrayfa.shared.navigation

import com.android.xrayfa.agent.AgentScreen
import kotlinx.serialization.Serializable

/**
 * Nested destinations on top of the Config/Home pager.
 * Mirrors Android Navigation3: Settings/QR/subscriptions/apps/logcat/route are pushed, not tabs.
 */
@Serializable
enum class RootOverlay {
    None,
    Settings,
    Subscriptions,
    QrScanner,
    Apps,
    Logcat,
    RouteSettings,
}

data class RootNavigationTarget(
    val tab: RootTab? = null,
    val stack: RootStackConfig = RootStackConfig.Idle,
    val overlay: RootOverlay = RootOverlay.None,
)

fun AgentScreen.toRootNavigation(): RootNavigationTarget =
    when (this) {
        AgentScreen.Home ->
            RootNavigationTarget(tab = RootTab.Home, stack = RootStackConfig.Idle, overlay = RootOverlay.None)
        AgentScreen.Config ->
            RootNavigationTarget(tab = RootTab.Config, stack = RootStackConfig.Idle, overlay = RootOverlay.None)
        AgentScreen.Subscriptions ->
            RootNavigationTarget(
                tab = RootTab.Config,
                stack = RootStackConfig.Subscriptions,
                overlay = RootOverlay.Subscriptions,
            )
        AgentScreen.Settings ->
            RootNavigationTarget(stack = RootStackConfig.Settings, overlay = RootOverlay.Settings)
        AgentScreen.Apps ->
            RootNavigationTarget(stack = RootStackConfig.Apps, overlay = RootOverlay.Apps)
        AgentScreen.RouteSettings ->
            RootNavigationTarget(stack = RootStackConfig.RouteSettings, overlay = RootOverlay.RouteSettings)
    }
