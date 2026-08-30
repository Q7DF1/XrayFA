package com.android.xrayfa.shared.navigation

import com.android.xrayfa.agent.AgentScreen
import com.arkivanov.decompose.router.pages.ChildPages
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.value.Value

interface RootComponent {
    val pages: Value<ChildPages<RootTab, Child>>

    val stack: Value<ChildStack<RootStackConfig, StackChild>>

    val settingsComponent: SettingsComponent

    fun selectTab(index: Int)

    fun selectTab(tab: RootTab) {
        selectTab(tab.ordinal)
    }

    /** Pager swipe/sync. Must not clear the stack (unlike [selectTab] from the bottom nav). */
    fun onPageSelected(index: Int)

    fun openSettings()

    fun openSubscriptions()

    fun openQrScanner()

    fun openApps()

    fun openLogcat()

    fun openRouteSettings()

    fun openNodeEdit(nodeId: Int)

    fun navigateBack()

    fun openAgentScreen(screen: AgentScreen)

    sealed class Child {
        class Config(val component: ConfigComponent) : Child()

        class Home(val component: HomeComponent) : Child()
    }

    sealed class StackChild {
        data object Idle : StackChild()
        data object Settings : StackChild()
        class Subscriptions(val component: SubscriptionComponent) : StackChild()
        data object QrScanner : StackChild()
        data object Apps : StackChild()
        data object Logcat : StackChild()
        data object RouteSettings : StackChild()
        class NodeEdit(val nodeId: Int) : StackChild()
    }
}
