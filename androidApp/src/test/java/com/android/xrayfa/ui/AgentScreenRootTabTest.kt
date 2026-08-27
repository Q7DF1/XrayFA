package com.android.xrayfa.ui

import com.android.xrayfa.agent.AgentScreen
import com.android.xrayfa.shared.navigation.RootTab
import org.junit.Assert.assertEquals
import org.junit.Test

class AgentScreenRootTabTest {

    @Test
    fun mapsToExistingRootTabs() {
        assertEquals(RootTab.Home, AgentScreen.Home.toRootTab())
        assertEquals(RootTab.Config, AgentScreen.Config.toRootTab())
        assertEquals(RootTab.Config, AgentScreen.Subscriptions.toRootTab())
        assertEquals(RootTab.Settings, AgentScreen.Settings.toRootTab())
        assertEquals(RootTab.Settings, AgentScreen.Apps.toRootTab())
        assertEquals(RootTab.Settings, AgentScreen.RouteSettings.toRootTab())
    }
}
