package com.android.xrayfa.shared.ui

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import com.android.xrayfa.model.Node
import com.android.xrayfa.shared.config.NodeFormEditor
import com.android.xrayfa.shared.navigation.RootComponent
import com.android.xrayfa.shared.navigation.RootStackConfig
import com.android.xrayfa.shared.navigation.RootTab
import com.android.xrayfa.shared.resources.*
import com.android.xrayfa.shared.ui.config.ConfigTabChromeState
import com.android.xrayfa.shared.ui.config.ConfigUiLabels
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
import com.android.xrayfa.shared.ui.transitions.LocalSharedTransitionScope
import com.android.xrayfa.shared.ui.transitions.LocalStackAnimationScope
import com.android.xrayfa.shared.ui.transitions.TransitionDestinations
import com.android.xrayfa.shared.ui.transitions.floatingNavOverlay
import com.android.xrayfa.shared.ui.transitions.sharedContainer
import com.arkivanov.decompose.ExperimentalDecomposeApi
import com.arkivanov.decompose.extensions.compose.experimental.stack.ChildStack
import com.arkivanov.decompose.extensions.compose.experimental.stack.animation.PredictiveBackParams
import com.arkivanov.decompose.extensions.compose.experimental.stack.animation.fade
import com.arkivanov.decompose.extensions.compose.experimental.stack.animation.plus
import com.arkivanov.decompose.extensions.compose.experimental.stack.animation.scale
import com.arkivanov.decompose.extensions.compose.experimental.stack.animation.stackAnimation
import com.arkivanov.decompose.extensions.compose.pages.ChildPages
import com.arkivanov.decompose.extensions.compose.pages.PagesScrollAnimation
import com.arkivanov.decompose.extensions.compose.stack.animation.predictiveback.materialPredictiveBackAnimatable
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import org.jetbrains.compose.resources.stringResource
import org.koin.mp.KoinPlatform

@OptIn(ExperimentalDecomposeApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun RootContent(
    component: RootComponent,
    modifier: Modifier = Modifier,
) {
    val stack by component.stack.subscribeAsState()
    val stackIdle = stack.active.configuration is RootStackConfig.Idle
    val configLabels = rememberConfigUiLabels()
    val configChromeState = rememberConfigTabChromeState()

    SharedTransitionLayout(modifier = modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
            val platformHooks = LocalPlatformRootHooks.current
            val settingsLabels = rememberSettingsUiLabels()
            val routeSettingsLabels = rememberRouteSettingsUiLabels()
            val pages by component.pages.subscribeAsState()
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

            ChildStack(
                stack = component.stack,
                modifier = Modifier.fillMaxSize(),
                animation =
                    stackAnimation(
                        animator = fade() + scale(),
                        predictiveBackParams = {
                            if (!platformHooks.usesDecomposePredictiveBack) {
                                null
                            } else {
                                PredictiveBackParams(
                                    backHandler = component.backHandler,
                                    onBack = component::navigateBack,
                                    animatable = ::materialPredictiveBackAnimatable,
                                )
                            }
                        },
                    ),
            ) { child ->
                CompositionLocalProvider(LocalStackAnimationScope provides this) {
                    val fill = Modifier.fillMaxSize()
                    when (val instance = child.instance) {
                        RootComponent.StackChild.Idle ->
                            IdleContent(
                                component = component,
                                chromeState = configChromeState,
                                configLabels = configLabels,
                            )
                        RootComponent.StackChild.Settings ->
                            SettingsTabScreen(
                                component = component.settingsComponent,
                                onBack = component::navigateBack,
                                onAppsClick = component::openApps,
                                onLogcatClick = component::openLogcat,
                                onRouteClick = component::openRouteSettings,
                                modifier = fill,
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
                                modifier = fill,
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
                                modifier =
                                    fill.sharedContainer(
                                        destination = TransitionDestinations.APPS,
                                        shape = RectangleShape,
                                        containerColor = MaterialTheme.colorScheme.background,
                                    ),
                            )
                        RootComponent.StackChild.Logcat ->
                            platformHooks.LogcatScreen(
                                onBack = component::navigateBack,
                                modifier =
                                    fill.sharedContainer(
                                        destination = TransitionDestinations.LOGCAT,
                                        shape = RectangleShape,
                                        containerColor = MaterialTheme.colorScheme.background,
                                    ),
                            )
                        RootComponent.StackChild.RouteSettings ->
                            SharedRouteSettingsScreen(
                                component = component.settingsComponent,
                                onBack = component::navigateBack,
                                labels = routeSettingsLabels,
                                modifier =
                                    fill.sharedContainer(
                                        destination = TransitionDestinations.ROUTE,
                                        shape = RectangleShape,
                                        containerColor = MaterialTheme.colorScheme.background,
                                    ),
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
                                                queryWasBlankAtTap =
                                                    cfg.state.value.searchQuery.isBlank(),
                                            )
                                    },
                                    modifier = fill,
                                )
                            }
                        }
                        is RootComponent.StackChild.NodeEdit -> {
                            val latestNode = configComponent?.nodeById(instance.nodeId)
                            val nodeState =
                                remember(instance.nodeId) { mutableStateOf<Node?>(null) }
                            if (nodeState.value == null && latestNode != null) {
                                nodeState.value = latestNode
                            }
                            val node = nodeState.value
                            val nodeFormEditor =
                                remember { KoinPlatform.getKoin().get<NodeFormEditor>() }
                            SharedEditScreen(
                                nodeId = instance.nodeId,
                                protocol = node?.protocolPrefix,
                                initialContent = node?.url,
                                initialRemark = node?.remark,
                                nodeFormEditor = nodeFormEditor,
                                onBack = component::navigateBack,
                                onSave = { form ->
                                    configComponent?.onSaveNodeEdit(instance.nodeId, form) { ok ->
                                        if (ok) component.navigateBack()
                                    }
                                },
                                labels = rememberEditUiLabels(),
                                modifier =
                                    fill.sharedContainer(
                                        destination =
                                            TransitionDestinations.nodeEdit(instance.nodeId),
                                        shape = RectangleShape,
                                        containerColor = MaterialTheme.colorScheme.background,
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalDecomposeApi::class)
@Composable
private fun IdleContent(
    component: RootComponent,
    chromeState: ConfigTabChromeState,
    configLabels: ConfigUiLabels,
) {
    val pages by component.pages.subscribeAsState()
    val selectedTab = pages.items.getOrNull(pages.selectedIndex)?.configuration ?: RootTab.Home

    Box(modifier = Modifier.fillMaxSize()) {
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
                        chromeState = chromeState,
                        onNodeSelectedNavigateHome = { component.selectTab(RootTab.Home) },
                        onOpenNodeEdit = component::openNodeEdit,
                        onOpenSubscriptions = component::openSubscriptions,
                        onOpenQrScanner = component::openQrScanner,
                    )
            }
        }

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
        Box(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
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
                        .floatingNavOverlay()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(bottom = FloatingNavBottomMargin, start = 16.dp, end = 16.dp),
            )
        }
    }
}
