package com.android.xrayfa.shared.navigation

import com.android.xrayfa.agent.AgentScreen
import com.arkivanov.decompose.router.pages.ChildPages
import com.arkivanov.decompose.value.Value

interface RootComponent {
    val pages: Value<ChildPages<RootTab, Child>>

    val overlay: Value<RootOverlay>

    val settingsComponent: SettingsComponent

    fun selectTab(index: Int)

    fun selectTab(tab: RootTab) {
        selectTab(tab.ordinal)
    }

    /** Pager swipe/sync. Must not clear overlays (unlike [selectTab] from the bottom nav). */
    fun onPageSelected(index: Int)

    fun openSettings()

    fun openSubscriptions()

    fun openQrScanner()

    fun openApps()

    fun openLogcat()

    fun openRouteSettings()

    fun navigateBack()

    fun openAgentScreen(screen: AgentScreen)

    sealed class Child {
        class Config(val component: ConfigComponent) : Child()

        class Home(val component: HomeComponent) : Child()
    }
}
