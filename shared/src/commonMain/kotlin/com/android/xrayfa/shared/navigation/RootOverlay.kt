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
    val overlay: RootOverlay = RootOverlay.None,
)

fun AgentScreen.toRootNavigation(): RootNavigationTarget =
    when (this) {
        AgentScreen.Home -> RootNavigationTarget(tab = RootTab.Home, overlay = RootOverlay.None)
        AgentScreen.Config -> RootNavigationTarget(tab = RootTab.Config, overlay = RootOverlay.None)
        AgentScreen.Subscriptions ->
            RootNavigationTarget(tab = RootTab.Config, overlay = RootOverlay.Subscriptions)
        AgentScreen.Settings -> RootNavigationTarget(overlay = RootOverlay.Settings)
        AgentScreen.Apps -> RootNavigationTarget(overlay = RootOverlay.Apps)
        AgentScreen.RouteSettings -> RootNavigationTarget(overlay = RootOverlay.RouteSettings)
    }
