package com.android.xrayfa.shared.navigation

import com.android.xrayfa.agent.AgentScreen
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.childContext
import com.arkivanov.decompose.router.pages.ChildPages
import com.arkivanov.decompose.router.pages.Pages
import com.arkivanov.decompose.router.pages.PagesNavigation
import com.arkivanov.decompose.router.pages.childPages
import com.arkivanov.decompose.router.pages.select
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value

class DefaultRootComponent(
    componentContext: ComponentContext,
    private val homeComponentFactory: HomeComponentFactory = defaultHomeComponentFactory(),
    private val configComponentFactory: ConfigComponentFactory = defaultConfigComponentFactory(),
    settingsComponentFactory: SettingsComponentFactory = defaultSettingsComponentFactory(),
) : RootComponent,
    ComponentContext by componentContext {
    private val navigation = PagesNavigation<RootTab>()
    private val overlayStack = ArrayList<RootOverlay>()
    private val _overlay = MutableValue(RootOverlay.None)

    override val overlay: Value<RootOverlay> = _overlay

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

    override fun selectTab(index: Int) {
        clearOverlays()
        navigation.select(index = index)
    }

    override fun onPageSelected(index: Int) {
        if (overlayStack.isNotEmpty()) {
            return
        }
        navigation.select(index = index)
    }

    override fun openSettings() {
        pushOverlay(RootOverlay.Settings)
    }

    override fun openSubscriptions() {
        pushOverlay(RootOverlay.Subscriptions)
        navigation.select(index = RootTab.Config.ordinal)
    }

    override fun openQrScanner() {
        pushOverlay(RootOverlay.QrScanner)
        navigation.select(index = RootTab.Config.ordinal)
    }

    override fun openApps() {
        pushOverlay(RootOverlay.Apps)
    }

    override fun openLogcat() {
        pushOverlay(RootOverlay.Logcat)
    }

    override fun openRouteSettings() {
        pushOverlay(RootOverlay.RouteSettings)
    }

    override fun navigateBack() {
        if (overlayStack.isEmpty()) {
            return
        }
        overlayStack.removeAt(overlayStack.lastIndex)
        _overlay.value = overlayStack.lastOrNull() ?: RootOverlay.None
    }

    override fun openAgentScreen(screen: AgentScreen) {
        val target = screen.toRootNavigation()
        when (target.overlay) {
            RootOverlay.None -> {
                val tab = target.tab ?: return
                selectTab(tab)
            }
            RootOverlay.Subscriptions -> openSubscriptions()
            RootOverlay.Settings -> openSettings()
            RootOverlay.Apps -> openApps()
            RootOverlay.RouteSettings -> openRouteSettings()
            RootOverlay.QrScanner -> openQrScanner()
            RootOverlay.Logcat -> openLogcat()
        }
    }

    private fun pushOverlay(item: RootOverlay) {
        if (item == RootOverlay.None) {
            clearOverlays()
            return
        }
        overlayStack.remove(item)
        overlayStack.add(item)
        _overlay.value = item
    }

    private fun clearOverlays() {
        overlayStack.clear()
        _overlay.value = RootOverlay.None
    }
}
