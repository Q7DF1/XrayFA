package com.android.xrayfa.ui

import com.android.xrayfa.agent.AgentScreen
import com.android.xrayfa.shared.navigation.RootStackConfig
import com.android.xrayfa.shared.navigation.RootTab
import com.android.xrayfa.shared.navigation.toRootNavigation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AgentScreenRootTabTest {

    @Test
    fun mapsToSharedRootNavigationMatchingAndroidDestinations() {
        val home = AgentScreen.Home.toRootNavigation()
        assertEquals(RootTab.Home, home.tab)
        assertEquals(RootStackConfig.Idle, home.stack)

        val config = AgentScreen.Config.toRootNavigation()
        assertEquals(RootTab.Config, config.tab)
        assertEquals(RootStackConfig.Idle, config.stack)

        val subscriptions = AgentScreen.Subscriptions.toRootNavigation()
        assertEquals(RootTab.Config, subscriptions.tab)
        assertEquals(RootStackConfig.Subscriptions, subscriptions.stack)

        val settings = AgentScreen.Settings.toRootNavigation()
        assertNull(settings.tab)
        assertEquals(RootStackConfig.Settings, settings.stack)

        val apps = AgentScreen.Apps.toRootNavigation()
        assertNull(apps.tab)
        assertEquals(RootStackConfig.Apps, apps.stack)

        val routeSettings = AgentScreen.RouteSettings.toRootNavigation()
        assertNull(routeSettings.tab)
        assertEquals(RootStackConfig.RouteSettings, routeSettings.stack)
    }

    @Test
    fun bottomNavHasConfigThenHomeOnly() {
        assertEquals(RootTab.Config, RootTab.entries[0])
        assertEquals(RootTab.Home, RootTab.entries[1])
        assertEquals(2, RootTab.entries.size)
    }

}
