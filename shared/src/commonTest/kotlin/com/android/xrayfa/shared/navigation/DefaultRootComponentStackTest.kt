package com.android.xrayfa.shared.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

class DefaultRootComponentStackTest {
    private fun active(component: DefaultRootComponent): RootStackConfig =
        component.stack.value.active.configuration

    private fun configs(component: DefaultRootComponent): List<RootStackConfig> =
        component.stack.value.items.map { it.configuration }

    @Test
    fun startsOnIdle() {
        val root = testRootComponent()
        assertEquals(listOf(RootStackConfig.Idle), configs(root))
    }

    @Test
    fun settingsThenAppsThenBackReturnsToSettings() {
        val root = testRootComponent()
        root.openSettings()
        root.openApps()
        assertEquals(RootStackConfig.Apps, active(root))
        root.navigateBack()
        assertEquals(RootStackConfig.Settings, active(root))
        root.navigateBack()
        assertEquals(RootStackConfig.Idle, active(root))
    }

    @Test
    fun secondOpenSettingsDoesNotStackTwice() {
        val root = testRootComponent()
        root.openSettings()
        root.openApps()
        root.openSettings()
        assertEquals(
            listOf(RootStackConfig.Idle, RootStackConfig.Settings),
            configs(root),
        )
    }

    @Test
    fun navigateBackOnIdleIsNoOp() {
        val root = testRootComponent()
        root.navigateBack()
        assertEquals(listOf(RootStackConfig.Idle), configs(root))
    }

    @Test
    fun nodeEditPopsOnBack() {
        val root = testRootComponent()
        root.openNodeEdit(0)
        assertEquals(RootStackConfig.NodeEdit(0), active(root))
        root.navigateBack()
        assertEquals(RootStackConfig.Idle, active(root))
    }

    @Test
    fun qrOnSubscriptionsDoesNotClearSubscriptions() {
        val root = testRootComponent()
        root.openSubscriptions()
        root.openQrScanner()
        assertEquals(
            listOf(RootStackConfig.Idle, RootStackConfig.Subscriptions, RootStackConfig.QrScanner),
            configs(root),
        )
        assertEquals(RootTab.Config, root.pages.value.items[root.pages.value.selectedIndex].configuration)
    }

    @Test
    fun selectTabClearsStack() {
        val root = testRootComponent()
        root.openSettings()
        root.selectTab(RootTab.Home)
        assertEquals(listOf(RootStackConfig.Idle), configs(root))
    }
}
