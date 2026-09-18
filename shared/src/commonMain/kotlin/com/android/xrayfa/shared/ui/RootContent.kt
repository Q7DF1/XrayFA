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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import com.android.xrayfa.shared.ui.config.SelectedNodeViewport
import org.jetbrains.compose.resources.painterResource
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.android.xrayfa.model.isJsonConfig
import com.android.xrayfa.shared.ui.config.JsonConfigEditScreen
import com.android.xrayfa.shared.ui.config.SharedSearchScreen
import com.android.xrayfa.shared.ui.config.rememberConfigTabChromeState
import com.android.xrayfa.shared.ui.nav.FloatingNavBackdropSource
import com.android.xrayfa.shared.ui.nav.FloatingNavBottomFade
import com.android.xrayfa.shared.ui.nav.FloatingNavBottomMargin
import com.android.xrayfa.shared.ui.nav.FloatingNavItem
import com.android.xrayfa.shared.ui.nav.ProvideFloatingNavBackdrop
import com.android.xrayfa.shared.ui.nav.XrayFloatingNav
import com.android.xrayfa.shared.ui.nav.toFloatingNavItem
import com.android.xrayfa.shared.ui.platform.LocalPlatformRootHooks
import com.android.xrayfa.shared.ui.settings.SharedRouteSettingsScreen
import com.android.xrayfa.shared.ui.subscription.SharedSubscriptionScreen
import com.android.xrayfa.shared.ui.transitions.LocalSharedTransitionScope
import com.android.xrayfa.shared.ui.transitions.LocalStackAnimationScope
import com.android.xrayfa.shared.ui.transitions.PlatformRowOuterCorner
import com.android.xrayfa.shared.ui.transitions.TransitionDestinations
import com.android.xrayfa.shared.ui.transitions.floatingNavOverlay
import com.android.xrayfa.shared.ui.transitions.rememberDestinationShape
import com.android.xrayfa.shared.ui.transitions.sharedContainer
import com.arkivanov.decompose.ExperimentalDecomposeApi
import com.arkivanov.decompose.extensions.compose.experimental.stack.ChildStack
import com.arkivanov.decompose.extensions.compose.experimental.stack.animation.PredictiveBackParams
import com.arkivanov.decompose.extensions.compose.experimental.stack.animation.fade
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
                        // 只淡入淡出。再叠一层整页 scale 会和共享元素各缩一次，结束时跳一下，
                        // 而且这段时间输入被动画吃掉。
                        animator = fade(),
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
                                modifier =
                                    Modifier.sharedContainer(
                                        destination = TransitionDestinations.SETTINGS,
                                        shape = rememberDestinationShape(startCorner = 20.dp),
                                        containerColor = MaterialTheme.colorScheme.background,
                                    )
                                    .fillMaxSize(),
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
                                modifier =
                                    Modifier.sharedContainer(
                                        destination = TransitionDestinations.SUBSCRIPTIONS,
                                        shape = rememberDestinationShape(startCorner = 20.dp),
                                        containerColor = MaterialTheme.colorScheme.background,
                                    )
                                    .fillMaxSize(),
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
                                modifier =
                                    Modifier.sharedContainer(
                                        destination = TransitionDestinations.QR,
                                        shape = rememberDestinationShape(startCorner = 20.dp),
                                        containerColor = MaterialTheme.colorScheme.background,
                                    )
                                    .fillMaxSize(),
                            )
                        RootComponent.StackChild.Apps ->
                            platformHooks.AppsScreen(
                                component = component.settingsComponent,
                                onBack = component::navigateBack,
                                modifier =
                                    Modifier.sharedContainer(
                                        destination = TransitionDestinations.APPS,
                                        shape =
                                            rememberDestinationShape(
                                                topStart = PlatformRowOuterCorner,
                                                topEnd = PlatformRowOuterCorner,
                                                bottomStart = 0.dp,
                                                bottomEnd = 0.dp,
                                            ),
                                        containerColor = MaterialTheme.colorScheme.background,
                                    )
                                    .fillMaxSize(),
                            )
                        RootComponent.StackChild.Logcat ->
                            platformHooks.LogcatScreen(
                                onBack = component::navigateBack,
                                modifier =
                                    Modifier.sharedContainer(
                                        destination = TransitionDestinations.LOGCAT,
                                        shape = RectangleShape,
                                        containerColor = MaterialTheme.colorScheme.background,
                                    )
                                    .fillMaxSize(),
                            )
                        RootComponent.StackChild.RouteSettings ->
                            SharedRouteSettingsScreen(
                                component = component.settingsComponent,
                                onBack = component::navigateBack,
                                labels = routeSettingsLabels,
                                modifier =
                                    Modifier.sharedContainer(
                                        destination = TransitionDestinations.ROUTE,
                                        shape =
                                            rememberDestinationShape(
                                                topStart = 0.dp,
                                                topEnd = 0.dp,
                                                bottomStart = PlatformRowOuterCorner,
                                                bottomEnd = PlatformRowOuterCorner,
                                            ),
                                        containerColor = MaterialTheme.colorScheme.background,
                                    )
                                    .fillMaxSize(),
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
                                    modifier =
                                        Modifier.sharedContainer(
                                            destination = TransitionDestinations.SEARCH,
                                            // 28dp 对应 64dp 圆形按钮的半径，起点看起来才是圆的。
                                            shape = rememberDestinationShape(startCorner = 28.dp),
                                            containerColor = MaterialTheme.colorScheme.surface,
                                        )
                                        .fillMaxSize(),
                                )
                            }
                        }
                        is RootComponent.StackChild.JsonConfigEdit -> {
                            configComponent?.let { JsonConfigEditScreen(instance.nodeId, it, component::navigateBack, Modifier.fillMaxSize()) }
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
                            if (node?.isJsonConfig == true) {
                                configComponent?.let { JsonConfigEditScreen(instance.nodeId, it, component::navigateBack, Modifier.fillMaxSize()) }
                            } else SharedEditScreen(
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
                                    Modifier.sharedContainer(
                                        destination =
                                            TransitionDestinations.nodeEdit(instance.nodeId),
                                        // 起点是列表里的矩形行，或顶栏那个圆形 Edit 按钮。
                                        shape =
                                            if (instance.nodeId > 0) {
                                                RectangleShape
                                            } else {
                                                rememberDestinationShape(startCorner = 20.dp)
                                            },
                                        containerColor = MaterialTheme.colorScheme.background,
                                    )
                                    .fillMaxSize(),
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

    ProvideFloatingNavBackdrop {
    Box(modifier = Modifier.fillMaxSize()) {
        // Only the tab pages feed the nav backdrop; the bottom fade and nav stay outside the source.
        FloatingNavBackdropSource(modifier = Modifier.fillMaxSize()) {
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
                        onOpenSearch = component::openSearch,
                        onOpenQrScanner = component::openQrScanner,
                            onOpenJsonConfig = component::openJsonConfigEdit,
                    )
            }
        }
        }

        val navItems =
            listOf(
                FloatingNavItem(
                    id = RootTab.Config.name,
                    icon = when {
                        selectedTab == RootTab.Config && chromeState.selectedNodeViewport == SelectedNodeViewport.Above -> Icons.Default.KeyboardArrowUp
                        selectedTab == RootTab.Config && chromeState.selectedNodeViewport == SelectedNodeViewport.Below -> Icons.Default.KeyboardArrowDown
                        else -> RootTab.Config.toFloatingNavItem().icon
                    },
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
                    val tab = if (item.id == RootTab.Config.name) RootTab.Config else RootTab.Home
                    if (tab == RootTab.Config && selectedTab == RootTab.Config) {
                        chromeState.requestLocateSelected = true
                    } else {
                        component.selectTab(tab)
                    }
                },
                trailingContent = {
                    Box(
                        modifier =
                            Modifier
                                .sharedContainer(
                                    destination = TransitionDestinations.SUBSCRIPTIONS,
                                    shape = CircleShape,
                                    // `XrayFloatingNav` 自己的 Surface 已经画了圆形底色，
                                    // 再叠一层会变色。
                                    containerColor = Color.Transparent,
                                )
                                .fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_subscription),
                            contentDescription = stringResource(Res.string.menu_subscription),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                },
                onTrailingClick = component::openSubscriptions,
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
}
