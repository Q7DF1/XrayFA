package com.android.xrayfa.ui

import com.android.xrayfa.agent.AgentScreen
import com.android.xrayfa.shared.navigation.RootOverlay
import com.android.xrayfa.shared.navigation.RootTab
import com.android.xrayfa.shared.navigation.toRootNavigation
import com.android.xrayfa.ui.navigation.Apps
import com.android.xrayfa.ui.navigation.Config
import com.android.xrayfa.ui.navigation.Home
import com.android.xrayfa.ui.navigation.RouteSettings
import com.android.xrayfa.ui.navigation.Settings
import com.android.xrayfa.ui.navigation.Subscription
import com.android.xrayfa.ui.navigation.toDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AgentScreenRootTabTest {

    @Test
    fun mapsToSharedRootNavigationMatchingAndroidDestinations() {
        val home = AgentScreen.Home.toRootNavigation()
        assertEquals(RootTab.Home, home.tab)
        assertEquals(RootOverlay.None, home.overlay)

        val config = AgentScreen.Config.toRootNavigation()
        assertEquals(RootTab.Config, config.tab)
        assertEquals(RootOverlay.None, config.overlay)

        val subscriptions = AgentScreen.Subscriptions.toRootNavigation()
        assertEquals(RootTab.Config, subscriptions.tab)
        assertEquals(RootOverlay.Subscriptions, subscriptions.overlay)

        val settings = AgentScreen.Settings.toRootNavigation()
        assertNull(settings.tab)
        assertEquals(RootOverlay.Settings, settings.overlay)

        val apps = AgentScreen.Apps.toRootNavigation()
        assertNull(apps.tab)
        assertEquals(RootOverlay.Apps, apps.overlay)

        val routeSettings = AgentScreen.RouteSettings.toRootNavigation()
        assertNull(routeSettings.tab)
        assertEquals(RootOverlay.RouteSettings, routeSettings.overlay)
    }

    @Test
    fun bottomNavHasConfigThenHomeOnly() {
        assertEquals(RootTab.Config, RootTab.entries[0])
        assertEquals(RootTab.Home, RootTab.entries[1])
        assertEquals(2, RootTab.entries.size)
    }

    @Test
    fun mapsToAndroidNavigationDestinations() {
        assertEquals(Home, AgentScreen.Home.toDestination())
        assertEquals(Config, AgentScreen.Config.toDestination())
        assertEquals(Subscription, AgentScreen.Subscriptions.toDestination())
        assertEquals(Settings, AgentScreen.Settings.toDestination())
        assertEquals(Apps, AgentScreen.Apps.toDestination())
        assertEquals(RouteSettings, AgentScreen.RouteSettings.toDestination())
    }
}
