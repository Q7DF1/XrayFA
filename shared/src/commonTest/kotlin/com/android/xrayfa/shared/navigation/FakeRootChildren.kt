package com.android.xrayfa.shared.navigation

import com.android.xrayfa.common.routing.DomainStrategy
import com.android.xrayfa.common.routing.RoutingMode
import com.android.xrayfa.common.routing.Rule
import com.android.xrayfa.datastore.SettingsState
import com.android.xrayfa.model.Node
import com.android.xrayfa.model.Subscription
import com.android.xrayfa.shared.config.NodeEditForm
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume

class FakeHomeComponent : HomeComponent {
    override val state: Value<HomeState> = MutableValue(HomeState())
    override fun onConnectToggle() = Unit
    override fun onTestDelay() = Unit
}

class FakeConfigComponent : ConfigComponent {
    override val state: Value<ConfigState> = MutableValue(ConfigState())
    override fun nodeById(id: Int): Node? = null
    override fun onSelectFilter(filterId: Int) = Unit
    override fun onSelectNode(nodeId: Int) = Unit
    override fun onToggleFavorite(nodeId: Int, favorite: Boolean) = Unit
    override fun onImportFromClipboard() = Unit
    override fun onImportFromLink(link: String) = Unit
    override fun onSaveNodeEdit(
        nodeId: Int,
        form: NodeEditForm,
        onDone: (Boolean) -> Unit,
    ) = Unit
    override fun onShowDeleteNode(node: Node) = Unit
    override fun onDismissDeleteNode() = Unit
    override fun onConfirmDeleteNode() = Unit
    override fun onShowDeleteAll() = Unit
    override fun onDismissDeleteAll() = Unit
    override fun onConfirmDeleteAll() = Unit
    override fun onSearch(query: String) = Unit
    override fun onTestAllDelays() = Unit
}

class FakeSettingsComponent : SettingsComponent {
    override val state: Value<SettingsState> = MutableValue(SettingsState())
    override val geoLiteDownload: Value<GeoLiteDownloadState> = MutableValue(GeoLiteDownloadState())
    override fun onSetTheme(themeCode: Int) = Unit
    override fun onSetBootAutoStart(enable: Boolean) = Unit
    override fun onSetAgentFunctionsEnabled(enable: Boolean) = Unit
    override fun onSetHideFromRecents(enable: Boolean) = Unit
    override fun onSetLanSocksProxyEnable(enable: Boolean) = Unit
    override fun onSetLanHttpProxyEnable(enable: Boolean) = Unit
    override fun onSetSendHwid(enable: Boolean) = Unit
    override fun onSetSocksPort(port: Int) = Unit
    override fun onSetHttpPort(port: Int) = Unit
    override fun onSetSocksUsername(username: String) = Unit
    override fun onSetSocksPassword(password: String) = Unit
    override fun onSetDnsIPv4(dns: String) = Unit
    override fun onSetDnsIPv6(dns: String) = Unit
    override fun onSetIpV6Enable(enable: Boolean) = Unit
    override fun onSetDelayTestUrl(url: String) = Unit
    override fun onSetRoutingMode(mode: RoutingMode) = Unit
    override fun onSetDomainStrategy(strategy: DomainStrategy) = Unit
    override fun onSetRoutingRules(rules: List<Rule>) = Unit
    override fun onDownloadGeoLite() = Unit
}

class FakeSubscriptionComponent : SubscriptionComponent {
    override val state: Value<SubscriptionState> = MutableValue(SubscriptionState())
    override fun isMarkDuplicate(mark: String, excludeSubscriptionId: Int): Boolean = false
    override fun openAddSheet() = Unit
    override fun openEditSheet(subscriptionId: Int) = Unit
    override fun closeSheet() = Unit
    override fun showDeleteDialog(subscription: Subscription) = Unit
    override fun dismissDeleteDialog() = Unit
    override fun confirmDelete() = Unit
    override fun addOrUpdateSubscription(subscription: Subscription, onSuccess: (Int) -> Unit) = Unit
    override fun refreshSubscription(subscription: Subscription, onSuccess: (Int) -> Unit) = Unit
}

fun testRootComponent(): DefaultRootComponent =
    DefaultRootComponent(
        componentContext = DefaultComponentContext(
            lifecycle = LifecycleRegistry().also { it.resume() },
        ),
        homeComponentFactory = { FakeHomeComponent() },
        configComponentFactory = { FakeConfigComponent() },
        settingsComponentFactory = { FakeSettingsComponent() },
        subscriptionComponentFactory = { FakeSubscriptionComponent() },
    )
