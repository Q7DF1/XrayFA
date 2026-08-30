package com.android.xrayfa.shared.navigation

import com.android.xrayfa.agent.AgentScreen
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.childContext
import com.arkivanov.decompose.router.pages.ChildPages
import com.arkivanov.decompose.router.pages.Pages
import com.arkivanov.decompose.router.pages.PagesNavigation
import com.arkivanov.decompose.router.pages.childPages
import com.arkivanov.decompose.router.pages.select
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.navigate
import com.arkivanov.decompose.router.stack.replaceAll
import com.arkivanov.decompose.value.Value

class DefaultRootComponent(
    componentContext: ComponentContext,
    private val homeComponentFactory: HomeComponentFactory = defaultHomeComponentFactory(),
    private val configComponentFactory: ConfigComponentFactory = defaultConfigComponentFactory(),
    settingsComponentFactory: SettingsComponentFactory = defaultSettingsComponentFactory(),
    private val subscriptionComponentFactory: SubscriptionComponentFactory = defaultSubscriptionComponentFactory(),
) : RootComponent,
    ComponentContext by componentContext {
    private val navigation = PagesNavigation<RootTab>()
    private val stackNavigation = StackNavigation<RootStackConfig>()

    override val settingsComponent: SettingsComponent =
        settingsComponentFactory(childContext("settings"))

    override val pages: Value<ChildPages<RootTab, RootComponent.Child>> =
        childPages(
            source = navigation,
            serializer = RootTab.serializer(),
            initialPages = {
                Pages(
                    items = listOf(RootTab.Config, RootTab.Home),
                    selectedIndex = RootTab.Home.ordinal,
                )
            },
        ) { tab, childContext ->
            when (tab) {
                RootTab.Config ->
                    RootComponent.Child.Config(
                        configComponentFactory(childContext),
                    )
                RootTab.Home ->
                    RootComponent.Child.Home(
                        homeComponentFactory(childContext),
                    )
            }
        }

    override val stack =
        childStack(
            source = stackNavigation,
            serializer = RootStackConfig.serializer(),
            initialStack = { listOf(RootStackConfig.Idle) },
            handleBackButton = false,
            childFactory = { config, childContext ->
                when (config) {
                    RootStackConfig.Idle -> RootComponent.StackChild.Idle
                    RootStackConfig.Settings -> RootComponent.StackChild.Settings
                    RootStackConfig.Subscriptions ->
                        RootComponent.StackChild.Subscriptions(subscriptionComponentFactory(childContext))
                    RootStackConfig.QrScanner -> RootComponent.StackChild.QrScanner
                    RootStackConfig.Apps -> RootComponent.StackChild.Apps
                    RootStackConfig.Logcat -> RootComponent.StackChild.Logcat
                    RootStackConfig.RouteSettings -> RootComponent.StackChild.RouteSettings
                    is RootStackConfig.NodeEdit -> RootComponent.StackChild.NodeEdit(config.nodeId)
                }
            },
        )

    private fun sameDestination(a: RootStackConfig, b: RootStackConfig): Boolean =
        when {
            a is RootStackConfig.NodeEdit && b is RootStackConfig.NodeEdit -> a.nodeId == b.nodeId
            a is RootStackConfig.NodeEdit || b is RootStackConfig.NodeEdit -> false
            else -> a::class == b::class
        }

    private fun bringOrPush(config: RootStackConfig) {
        stackNavigation.navigate { current ->
            val base = if (current.firstOrNull() is RootStackConfig.Idle) current else listOf(RootStackConfig.Idle) + current
            val existingIndex = base.indexOfFirst { sameDestination(it, config) }
            if (existingIndex >= 0) {
                base.take(existingIndex + 1)
            } else {
                base + config
            }
        }
    }

    private fun clearStack() {
        stackNavigation.replaceAll(RootStackConfig.Idle)
    }

    private fun isSubscriptionsActive(): Boolean =
        stack.value.active.configuration is RootStackConfig.Subscriptions

    override fun selectTab(index: Int) {
        clearStack()
        navigation.select(index = index)
    }

    override fun onPageSelected(index: Int) {
        if (stack.value.active.configuration !is RootStackConfig.Idle) return
        navigation.select(index = index)
    }

    override fun openSettings() = bringOrPush(RootStackConfig.Settings)
    override fun openSubscriptions() {
        bringOrPush(RootStackConfig.Subscriptions)
        navigation.select(index = RootTab.Config.ordinal)
    }
    override fun openQrScanner() {
        val stayOnSubscriptions = isSubscriptionsActive()
        bringOrPush(RootStackConfig.QrScanner)
        if (!stayOnSubscriptions) {
            navigation.select(index = RootTab.Config.ordinal)
        }
    }
    override fun openApps() = bringOrPush(RootStackConfig.Apps)
    override fun openLogcat() = bringOrPush(RootStackConfig.Logcat)
    override fun openRouteSettings() = bringOrPush(RootStackConfig.RouteSettings)
    override fun openNodeEdit(nodeId: Int) = bringOrPush(RootStackConfig.NodeEdit(nodeId))

    override fun navigateBack() {
        stackNavigation.navigate { current ->
            if (current.size <= 1) current else current.dropLast(1)
        }
    }

    override fun openAgentScreen(screen: AgentScreen) {
        val target = screen.toRootNavigation()
        when (val dest = target.stack) {
            RootStackConfig.Idle -> target.tab?.let { selectTab(it) }
            RootStackConfig.Subscriptions -> openSubscriptions()
            RootStackConfig.Settings -> openSettings()
            RootStackConfig.Apps -> openApps()
            RootStackConfig.RouteSettings -> openRouteSettings()
            RootStackConfig.QrScanner -> openQrScanner()
            RootStackConfig.Logcat -> openLogcat()
            is RootStackConfig.NodeEdit -> openNodeEdit(dest.nodeId)
        }
    }
}
