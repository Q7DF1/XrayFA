package com.android.xrayfa.shared.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.xrayfa.model.Node
import com.android.xrayfa.shared.config.NodeFormEditor
import com.android.xrayfa.shared.navigation.ConfigComponent
import com.android.xrayfa.shared.navigation.HomeComponent
import com.android.xrayfa.shared.navigation.NodeEditTarget
import com.android.xrayfa.shared.navigation.RootComponent
import com.android.xrayfa.shared.navigation.RootOverlay
import com.android.xrayfa.shared.navigation.RootTab
import com.android.xrayfa.shared.navigation.SettingsComponent
import com.android.xrayfa.shared.navigation.rememberSubscriptionComponent
import com.android.xrayfa.shared.resources.*
import com.android.xrayfa.shared.ui.config.SharedConfigImportMenu
import com.android.xrayfa.shared.ui.config.SharedConfigSection
import com.android.xrayfa.shared.ui.config.SharedEditScreen
import com.android.xrayfa.shared.ui.home.HomeTopBar
import com.android.xrayfa.shared.ui.nav.FloatingNavItem
import com.android.xrayfa.shared.ui.nav.XrayFloatingNav
import com.android.xrayfa.shared.ui.nav.toFloatingNavItem
import com.android.xrayfa.shared.ui.platform.LocalPlatformRootHooks
import com.android.xrayfa.shared.ui.settings.SharedRouteSettingsScreen
import com.android.xrayfa.shared.ui.settings.SharedSettingsAboutSection
import com.android.xrayfa.shared.ui.settings.SharedSettingsGeneralSection
import com.android.xrayfa.shared.ui.settings.SharedSettingsPlatformSection
import com.android.xrayfa.shared.ui.settings.SharedSettingsSubscriptionSection
import com.android.xrayfa.shared.ui.subscription.SharedSubscriptionScreen
import com.arkivanov.decompose.extensions.compose.pages.ChildPages
import com.arkivanov.decompose.extensions.compose.pages.PagesScrollAnimation
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.mp.KoinPlatform

@Composable
fun RootContent(
    component: RootComponent,
    modifier: Modifier = Modifier,
    openQrScannerRequest: Boolean = false,
    onOpenQrScannerRequestConsumed: () -> Unit = {},
) {
    val pages by component.pages.subscribeAsState()
    val overlay by component.overlay.subscribeAsState()
    val selectedTab = pages.items.getOrNull(pages.selectedIndex)?.configuration ?: RootTab.Home
    var configChromeCovered by remember { mutableStateOf(false) }
    val showBottomNav =
        overlay == RootOverlay.None &&
            (selectedTab == RootTab.Home || !configChromeCovered)
    val density = LocalDensity.current
    var bottomNavHeight by remember { mutableStateOf(72.dp) }

    LaunchedEffect(openQrScannerRequest) {
        if (openQrScannerRequest) {
            component.openQrScanner()
            onOpenQrScannerRequestConsumed()
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        val platformHooks = LocalPlatformRootHooks.current
        platformHooks.SystemBackHandler(
            enabled = overlay != RootOverlay.None,
            onBack = component::navigateBack,
        )

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
                        bottomNavPadding = bottomNavHeight,
                    )
                is RootComponent.Child.Config ->
                    ConfigTabScreen(
                        component = child.component,
                        onNodeSelectedNavigateHome = { component.selectTab(RootTab.Home) },
                        onChromeCovered = { configChromeCovered = it },
                        onOpenSubscriptions = component::openSubscriptions,
                        onOpenQrScanner = component::openQrScanner,
                        bottomNavPadding = bottomNavHeight,
                    )
            }
        }

        RootOverlayHost(
            component = component,
            overlay = overlay,
            configComponent =
                pages.items
                    .map { it.instance }
                    .filterIsInstance<RootComponent.Child.Config>()
                    .firstOrNull()
                    ?.component,
        )

        AnimatedVisibility(
            visible = showBottomNav,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .onGloballyPositioned { coordinates ->
                        bottomNavHeight = with(density) { coordinates.size.height.toDp() }
                    },
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
            XrayFloatingNav(
                items = navItems,
                selectedId = selectedTab.name,
                onItemSelected = { item ->
                    component.selectTab(
                        if (item.id == RootTab.Config.name) RootTab.Config else RootTab.Home,
                    )
                },
                modifier = Modifier.padding(bottom = 8.dp, start = 16.dp, end = 16.dp),
            )
        }
    }
}

@Composable
private fun RootOverlayHost(
    component: RootComponent,
    overlay: RootOverlay,
    configComponent: ConfigComponent?,
) {
    val platformHooks = LocalPlatformRootHooks.current
    val settingsLabels = rememberSettingsUiLabels()
    val routeSettingsLabels = rememberRouteSettingsUiLabels()
    val subscriptionComponent = rememberSubscriptionComponent()
    var displayedOverlay by remember { mutableStateOf(overlay) }
    if (overlay != RootOverlay.None) {
        displayedOverlay = overlay
    }
    val overlayVisible = remember { MutableTransitionState(false) }
    overlayVisible.targetState = overlay != RootOverlay.None

    AnimatedVisibility(
        visibleState = overlayVisible,
        enter = slideInHorizontally { it } + fadeIn(),
        exit = slideOutHorizontally { it } + fadeOut(),
        modifier = Modifier.fillMaxSize(),
    ) {
        AnimatedContent(
            targetState = displayedOverlay,
            transitionSpec = {
                (slideInHorizontally { it } + fadeIn()) togetherWith
                    (slideOutHorizontally { -it / 4 } + fadeOut())
            },
            modifier = Modifier.fillMaxSize(),
            label = "rootOverlay",
        ) { current ->
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
            ) {
                when (current) {
                RootOverlay.None -> Unit
                RootOverlay.Settings ->
                    SettingsTabScreen(
                        component = component.settingsComponent,
                        onBack = component::navigateBack,
                        onAppsClick = component::openApps,
                        onLogcatClick = component::openLogcat,
                        onRouteClick = component::openRouteSettings,
                    )
                RootOverlay.Subscriptions ->
                    SharedSubscriptionScreen(
                        component = subscriptionComponent,
                        onBack = component::navigateBack,
                        labels = rememberSubscriptionUiLabels(),
                        onSubscriptionApplied = { subscriptionId ->
                            configComponent?.onSelectFilter(subscriptionId)
                            component.navigateBack()
                        },
                        onScanQr = component::openQrScanner,
                    )
                RootOverlay.QrScanner ->
                    platformHooks.QrScannerScreen(
                        onResult = { result ->
                            configComponent?.onImportFromLink(result)
                            component.navigateBack()
                        },
                        onBack = component::navigateBack,
                        title = settingsLabels.qrScannerTitle,
                        permissionRequiredMessage = settingsLabels.qrPermissionRequired,
                    )
                RootOverlay.Apps ->
                    platformHooks.AppsScreen(
                        component = component.settingsComponent,
                        onBack = component::navigateBack,
                    )
                RootOverlay.Logcat -> platformHooks.LogcatScreen(onBack = component::navigateBack)
                RootOverlay.RouteSettings ->
                    SharedRouteSettingsScreen(
                        component = component.settingsComponent,
                        onBack = component::navigateBack,
                        labels = routeSettingsLabels,
                    )
            }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigTabScreen(
    component: ConfigComponent,
    onNodeSelectedNavigateHome: () -> Unit,
    onChromeCovered: (Boolean) -> Unit,
    onOpenSubscriptions: () -> Unit,
    onOpenQrScanner: () -> Unit,
    bottomNavPadding: Dp,
) {
    val platformHooks = LocalPlatformRootHooks.current
    val configLabels = rememberConfigUiLabels()
    val editLabels = rememberEditUiLabels()
    val settingsLabels = rememberSettingsUiLabels()
    val configState by component.state.subscribeAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var searchExpanded by remember { mutableStateOf(false) }
    var shareNode by remember { mutableStateOf<Node?>(null) }
    var showBugReport by remember { mutableStateOf(false) }
    val covering = configState.nodeEditTarget != null
    LaunchedEffect(covering) { onChromeCovered(covering) }
    DisposableEffect(Unit) { onDispose { onChromeCovered(false) } }

    configState.nodeEditTarget?.let { target ->
        platformHooks.SystemBackHandler(
            enabled = true,
            onBack = component::onCloseNodeEdit,
        )
        val nodeFormEditor = remember { KoinPlatform.getKoin().get<NodeFormEditor>() }
        when (target) {
            is NodeEditTarget.Create ->
                SharedEditScreen(
                    nodeId = 0,
                    protocol = null,
                    initialContent = null,
                    initialRemark = null,
                    nodeFormEditor = nodeFormEditor,
                    onBack = component::onCloseNodeEdit,
                    onSave = component::onSaveNodeEdit,
                    labels = editLabels,
                )
            is NodeEditTarget.Edit ->
                SharedEditScreen(
                    nodeId = target.node.id,
                    protocol = target.node.protocolPrefix,
                    initialContent = target.node.url,
                    initialRemark = target.node.remark,
                    nodeFormEditor = nodeFormEditor,
                    onBack = component::onCloseNodeEdit,
                    onSave = component::onSaveNodeEdit,
                    labels = editLabels,
                )
        }
        return
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.config), fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { searchExpanded = !searchExpanded }) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = configLabels.searchLabel,
                        )
                    }
                    IconButton(onClick = component::onOpenCreateNode) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = configLabels.createConfigLabel,
                        )
                    }
                    SharedConfigImportMenu(
                        onImportFromClipboard = component::onImportFromClipboard,
                        onManageSubscriptions = onOpenSubscriptions,
                        onScanQr = onOpenQrScanner,
                        importFromClipboardLabel = stringResource(Res.string.clipboard_import),
                        manageSubscriptionsLabel = stringResource(Res.string.menu_subscription),
                        scanQrLabel = settingsLabels.scanQrLabel,
                        additionalMenuItems = { dismiss ->
                            DropdownMenuItem(
                                text = { Text(configLabels.locateSelectedLabel) },
                                leadingIcon = {
                                    Icon(Icons.Outlined.Star, contentDescription = null)
                                },
                                onClick = {
                                    dismiss()
                                    scope.launch {
                                        val index = configState.nodes.indexOfFirst { it.selected }
                                        if (index >= 0) {
                                            listState.animateScrollToItem(index)
                                        }
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(configLabels.deleteAllLabel) },
                                leadingIcon = {
                                    Icon(Icons.Outlined.DeleteForever, contentDescription = null)
                                },
                                onClick = {
                                    dismiss()
                                    component.onShowDeleteAll()
                                },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(configLabels.bugReportLabel) },
                                leadingIcon = {
                                    Icon(Icons.Outlined.BugReport, contentDescription = null)
                                },
                                onClick = {
                                    dismiss()
                                    showBugReport = true
                                },
                            )
                        },
                    )
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = MaterialTheme.colorScheme.surface,
                    ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            if (searchExpanded) {
                OutlinedTextField(
                    value = configState.searchQuery,
                    onValueChange = component::onSearch,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    singleLine = true,
                    label = { Text(configLabels.searchLabel) },
                )
            }
            SharedConfigSection(
                component = component,
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(bottom = bottomNavPadding),
                labels = configLabels,
                listState = listState,
                nodeDelayMap = configState.nodeDelayMap,
                onNodeSelected = { node ->
                    component.onSelectNode(node.id)
                    onNodeSelectedNavigateHome()
                },
                onEmptyAddClick = component::onOpenCreateNode,
                onEditNode = { node -> component.onOpenEditNode(node.id) },
                onDeleteNode = component::onShowDeleteNode,
                onShareNode = { node -> shareNode = node },
                filterTrailingContent = {
                    IconButton(onClick = component::onTestAllDelays) {
                        Icon(
                            imageVector = Icons.Outlined.Speed,
                            contentDescription = configLabels.speedTestAllLabel,
                            tint =
                                if (configState.testingAll) {
                                    MaterialTheme.colorScheme.secondary
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                            modifier = Modifier.size(20.dp),
                        )
                    }
                },
            )
        }
    }

    configState.deleteTarget?.let { node ->
        AlertDialog(
            onDismissRequest = component::onDismissDeleteNode,
            title = { Text(configLabels.deleteNodeTitle) },
            text = { Text(node.remark?.ifBlank { node.url } ?: node.url) },
            confirmButton = {
                TextButton(onClick = component::onConfirmDeleteNode) {
                    Text(configLabels.deleteLabel)
                }
            },
            dismissButton = {
                TextButton(onClick = component::onDismissDeleteNode) {
                    Text(configLabels.cancelLabel)
                }
            },
        )
    }

    if (configState.pendingDeleteAll) {
        AlertDialog(
            onDismissRequest = component::onDismissDeleteAll,
            title = { Text(configLabels.deleteAllTitle) },
            text = { Text(configLabels.deleteAllConfirm) },
            confirmButton = {
                TextButton(onClick = component::onConfirmDeleteAll) {
                    Text(configLabels.deleteLabel)
                }
            },
            dismissButton = {
                TextButton(onClick = component::onDismissDeleteAll) {
                    Text(configLabels.cancelLabel)
                }
            },
        )
    }

    shareNode?.let { node ->
        platformHooks.ShareNode(node = node, onDismiss = { shareNode = null })
    }
    platformHooks.BugReport(visible = showBugReport, onDismiss = { showBugReport = false })
}

@Composable
private fun HomeTabScreen(
    component: HomeComponent,
    onSettingsClick: () -> Unit,
    bottomNavPadding: Dp,
) {
    val homeLabels = rememberHomeUiLabels()
    val platformHooks = LocalPlatformRootHooks.current
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            HomeTopBar(
                title = stringResource(Res.string.home),
                onSettingsClick = onSettingsClick,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        platformHooks.HomeSection(
            component = component,
            labels = homeLabels,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(bottom = bottomNavPadding),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsTabScreen(
    component: SettingsComponent,
    onBack: () -> Unit,
    onAppsClick: () -> Unit,
    onLogcatClick: () -> Unit,
    onRouteClick: () -> Unit,
) {
    val settingsLabels = rememberSettingsUiLabels()
    val platformHooks = LocalPlatformRootHooks.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.settings_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = settingsLabels.cancelLabel,
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = MaterialTheme.colorScheme.surface,
                    ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState()),
        ) {
            SharedSettingsGeneralSection(
                component = component,
                scrollEnabled = false,
                labels = settingsLabels,
                additionalGeneralContent = {
                    with(platformHooks) { SettingsGeneralExtras(component) }
                },
                additionalNetworkContent = {
                    with(platformHooks) { SettingsNetworkExtras(component) }
                },
            )
            SharedSettingsPlatformSection(
                labels = settingsLabels,
                onAppsClick = onAppsClick,
                onLogcatClick = onLogcatClick,
                onRouteClick = onRouteClick,
            )
            SharedSettingsSubscriptionSection(component = component, labels = settingsLabels)
            SharedSettingsAboutSection(component = component, labels = settingsLabels)
        }
    }
}
