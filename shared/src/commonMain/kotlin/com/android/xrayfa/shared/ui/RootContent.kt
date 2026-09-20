package com.android.xrayfa.shared.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.xrayfa.model.Node
import com.android.xrayfa.shared.config.NodeFormEditor
import com.android.xrayfa.shared.navigation.RootComponent
import com.android.xrayfa.shared.navigation.RootStackConfig
import com.android.xrayfa.shared.navigation.RootTab
import com.android.xrayfa.shared.resources.*
import com.android.xrayfa.shared.ui.config.OverlayScrollPending
import com.android.xrayfa.shared.ui.config.SharedEditScreen
import com.android.xrayfa.shared.ui.config.SharedSearchScreen
import com.android.xrayfa.shared.ui.config.rememberConfigTabChromeState
import com.android.xrayfa.shared.ui.nav.FloatingNavBottomFade
import com.android.xrayfa.shared.ui.nav.FloatingNavBottomMargin
import com.android.xrayfa.shared.ui.nav.FloatingNavItem
import com.android.xrayfa.shared.ui.nav.XrayFloatingNav
import com.android.xrayfa.shared.ui.nav.toFloatingNavItem
import com.android.xrayfa.shared.ui.platform.LocalPlatformRootHooks
import com.android.xrayfa.shared.ui.settings.SharedRouteSettingsScreen
import com.android.xrayfa.shared.ui.subscription.SharedSubscriptionScreen
import com.arkivanov.decompose.ExperimentalDecomposeApi
import com.arkivanov.decompose.extensions.compose.pages.ChildPages
import com.arkivanov.decompose.extensions.compose.pages.PagesScrollAnimation
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.stack.animation.predictiveback.predictiveBackAnimation
import com.arkivanov.decompose.extensions.compose.stack.animation.slide
import com.arkivanov.decompose.extensions.compose.stack.animation.stackAnimation
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import org.jetbrains.compose.resources.stringResource
import org.koin.mp.KoinPlatform

@OptIn(ExperimentalDecomposeApi::class)
@Composable
fun RootContent(
    component: RootComponent,
    modifier: Modifier = Modifier,
) {
    val pages by component.pages.subscribeAsState()
    val stack by component.stack.subscribeAsState()
    val stackIdle = stack.active.configuration is RootStackConfig.Idle
    val selectedTab = pages.items.getOrNull(pages.selectedIndex)?.configuration ?: RootTab.Home
    val showBottomNav = stackIdle
    val configLabels = rememberConfigUiLabels()
    val configChromeState = rememberConfigTabChromeState()

    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        val platformHooks = LocalPlatformRootHooks.current
        val settingsLabels = rememberSettingsUiLabels()
        val routeSettingsLabels = rememberRouteSettingsUiLabels()
        val configComponent =
            pages.items
                .map { it.instance }
                .filterIsInstance<RootComponent.Child.Config>()
                .firstOrNull()
                ?.component
        if (!platformHooks.usesDecomposePredictiveBack) {
            platformHooks.SystemBackHandler(
                enabled = !stackIdle,
                onBack = component::navigateBack,
            )
        }

        ChildPages(
            modifier = Modifier.fillMaxSize(),
            pages = component.pages,
            onPageSelected = component::onPageSelected,
            scrollAnimation = PagesScrollAnimation.Default,
        ) { _, child ->
            when (child) {
                is RootComponent.Child.Home ->
                    HomeTabScreen(
                        component = child.component,
                        onSettingsClick = component::openSettings,
                    )
                is RootComponent.Child.Config ->
                    ConfigTabScreen(
                        component = child.component,
                        chromeState = configChromeState,
                        onNodeSelectedNavigateHome = { component.selectTab(RootTab.Home) },
                        onOpenNodeEdit = component::openNodeEdit,
                        onOpenSubscriptions = component::openSubscriptions,
                        onOpenQrScanner = component::openQrScanner,
                    )
            }
        }

        Children(
            stack = component.stack,
            modifier = Modifier.fillMaxSize(),
            animation =
                if (platformHooks.usesDecomposePredictiveBack) {
                    predictiveBackAnimation(
                        backHandler = component.backHandler,
                        fallbackAnimation = stackAnimation(slide()),
                        onBack = component::navigateBack,
                    )
                } else {
                    stackAnimation(slide())
                },
        ) { child ->
            val fill = Modifier.fillMaxSize()
            when (val instance = child.instance) {
                RootComponent.StackChild.Idle -> Unit
                RootComponent.StackChild.Settings ->
                    SettingsTabScreen(
                        component = component.settingsComponent,
                        onBack = component::navigateBack,
                        onAppsClick = component::openApps,
                        onLogcatClick = component::openLogcat,
                        onRouteClick = component::openRouteSettings,
                    )
                is RootComponent.StackChild.Subscriptions ->
                    SharedSubscriptionScreen(
                        component = instance.component,
                        onBack = component::navigateBack,
                        labels = rememberSubscriptionUiLabels(),
                        onSubscriptionApplied = { subscriptionId ->
                            configComponent?.onSelectFilter(subscriptionId)
                            component.navigateBack()
                        },
                        onScanQr = component::openQrScanner,
                    )
                RootComponent.StackChild.QrScanner ->
                    platformHooks.QrScannerScreen(
                        onResult = { result ->
                            configComponent?.onImportFromLink(result)
                            component.navigateBack()
                        },
                        onBack = component::navigateBack,
                        title = settingsLabels.qrScannerTitle,
                        permissionRequiredMessage = settingsLabels.qrPermissionRequired,
                    )
                RootComponent.StackChild.Apps ->
                    platformHooks.AppsScreen(
                        component = component.settingsComponent,
                        onBack = component::navigateBack,
                    )
                RootComponent.StackChild.Logcat ->
                    platformHooks.LogcatScreen(onBack = component::navigateBack)
                RootComponent.StackChild.RouteSettings ->
                    SharedRouteSettingsScreen(
                        component = component.settingsComponent,
                        onBack = component::navigateBack,
                        labels = routeSettingsLabels,
                    )
                RootComponent.StackChild.Search -> {
                    val cfg = configComponent
                    if (cfg != null) {
                        SharedSearchScreen(
                            component = cfg,
                            labels = configLabels,
                            backContentDescription = settingsLabels.cancelLabel,
                            onBack = component::navigateBack,
                            onResultChosen = { nodeId ->
                                configChromeState.pendingOverlayScroll =
                                    OverlayScrollPending(
                                        nodeId = nodeId,
                                        nodesAtTap = cfg.state.value.nodes,
                                        queryWasBlankAtTap = cfg.state.value.searchQuery.isBlank(),
                                    )
                            },
                            modifier = fill,
                        )
                    }
                }
                is RootComponent.StackChild.NodeEdit -> {
                    val latestNode = configComponent?.nodeById(instance.nodeId)
                    val nodeState = remember(instance.nodeId) { mutableStateOf<Node?>(null) }
                    if (nodeState.value == null && latestNode != null) {
                        nodeState.value = latestNode
                    }
                    val node = nodeState.value
                    val nodeFormEditor = remember { KoinPlatform.getKoin().get<NodeFormEditor>() }
                    SharedEditScreen(
                        nodeId = instance.nodeId,
                        protocol = node?.protocolPrefix,
                        initialContent = node?.url,
                        initialRemark = node?.remark,
                        nodeFormEditor = nodeFormEditor,
                        onBack = component::navigateBack,
                        onSave = { form ->
                            configComponent?.onSaveNodeEdit(instance.nodeId, form) { success ->
                                if (success) component.navigateBack()
                            }
                        },
                        labels = rememberEditUiLabels(),
                        modifier = fill,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = showBottomNav,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
        ) {
            val navItems =
                listOf(
                    FloatingNavItem(
                        id = RootTab.Config.name,
                        icon = RootTab.Config.toFloatingNavItem().icon,
                        label = stringResource(Res.string.config),
                    ),
                    FloatingNavItem(
                        id = RootTab.Home.name,
                        icon = RootTab.Home.toFloatingNavItem().icon,
                        label = stringResource(Res.string.home),
                    ),
                )
            Box(modifier = Modifier.fillMaxWidth()) {
                FloatingNavBottomFade(modifier = Modifier.align(Alignment.BottomCenter))
                XrayFloatingNav(
                    items = navItems,
                    selectedId = selectedTab.name,
                    onItemSelected = { item ->
                        component.selectTab(
                            if (item.id == RootTab.Config.name) RootTab.Config else RootTab.Home,
                        )
                    },
                    trailingContent = {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = configLabels.searchLabel,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.size(26.dp),
                        )
                    },
                    onTrailingClick = component::openSearch,
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(bottom = FloatingNavBottomMargin, start = 16.dp, end = 16.dp),
                )
            }
        }
    }
}
