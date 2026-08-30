package com.android.xrayfa.shared.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.xrayfa.model.Node
import com.android.xrayfa.shared.config.NodeFormEditor
import com.android.xrayfa.shared.navigation.ConfigComponent
import com.android.xrayfa.shared.navigation.HomeComponent
import com.android.xrayfa.shared.navigation.RootComponent
import com.android.xrayfa.shared.navigation.RootStackConfig
import com.android.xrayfa.shared.navigation.RootTab
import com.android.xrayfa.shared.navigation.SettingsComponent
import com.android.xrayfa.shared.resources.*
import com.android.xrayfa.shared.ui.chrome.SharedListScaffold
import com.android.xrayfa.shared.ui.config.SharedConfigImportMenu
import com.android.xrayfa.shared.ui.config.SharedConfigSection
import com.android.xrayfa.shared.ui.config.SharedEditScreen
import com.android.xrayfa.shared.ui.home.HomeTopBar
import com.android.xrayfa.shared.ui.nav.FloatingNavBarHeight
import com.android.xrayfa.shared.ui.nav.FloatingNavBottomMargin
import com.android.xrayfa.shared.ui.nav.FloatingNavContentClearance
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
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.stack.animation.predictiveback.predictiveBackAnimation
import com.arkivanov.decompose.extensions.compose.stack.animation.slide
import com.arkivanov.decompose.extensions.compose.stack.animation.stackAnimation
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
    val stack by component.stack.subscribeAsState()
    val stackIdle = stack.active.configuration is RootStackConfig.Idle
    val selectedTab = pages.items.getOrNull(pages.selectedIndex)?.configuration ?: RootTab.Home
    val searchExpandedCoversNav = false
    val floatingNavVisible = remember { mutableStateOf(true) }
    val hideNavOnScroll =
        remember {
            object : NestedScrollConnection {
                override fun onPreScroll(
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    when {
                        available.y < -8f -> floatingNavVisible.value = false
                        available.y > 8f -> floatingNavVisible.value = true
                    }
                    return Offset.Zero
                }
            }
        }
    val showBottomNav = stackIdle && !searchExpandedCoversNav && floatingNavVisible.value

    LaunchedEffect(selectedTab, stackIdle) {
        if (stackIdle) {
            floatingNavVisible.value = true
        }
    }

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
        val settingsLabels = rememberSettingsUiLabels()
        val routeSettingsLabels = rememberRouteSettingsUiLabels()
        val configComponent =
            pages.items
                .map { it.instance }
                .filterIsInstance<RootComponent.Child.Config>()
                .firstOrNull()
                ?.component
        platformHooks.SystemBackHandler(
            enabled = !stackIdle,
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
                        hideNavOnScroll = hideNavOnScroll,
                    )
                is RootComponent.Child.Config ->
                    ConfigTabScreen(
                        component = child.component,
                        onNodeSelectedNavigateHome = { component.selectTab(RootTab.Home) },
                        onOpenNodeEdit = component::openNodeEdit,
                        onOpenSubscriptions = component::openSubscriptions,
                        onOpenQrScanner = component::openQrScanner,
                        hideNavOnScroll = hideNavOnScroll,
                    )
            }
        }

        Children(
            stack = component.stack,
            modifier = Modifier.fillMaxSize(),
            animation =
                predictiveBackAnimation(
                    backHandler = component.backHandler,
                    fallbackAnimation = stackAnimation(slide()),
                    onBack = component::navigateBack,
                ),
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
                is RootComponent.StackChild.NodeEdit -> {
                    val node =
                        configComponent
                            ?.state
                            ?.value
                            ?.nodes
                            ?.firstOrNull { it.id == instance.nodeId }
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
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars),
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
                modifier = Modifier.padding(bottom = FloatingNavBottomMargin, start = 16.dp, end = 16.dp),
            )
        }
    }
}

@Composable
private fun ConfigTabScreen(
    component: ConfigComponent,
    onNodeSelectedNavigateHome: () -> Unit,
    onOpenNodeEdit: (Int) -> Unit,
    onOpenSubscriptions: () -> Unit,
    onOpenQrScanner: () -> Unit,
    hideNavOnScroll: NestedScrollConnection,
) {
    val platformHooks = LocalPlatformRootHooks.current
    val configLabels = rememberConfigUiLabels()
    val settingsLabels = rememberSettingsUiLabels()
    val configState by component.state.subscribeAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var searchExpanded by remember { mutableStateOf(false) }
    var shareNode by remember { mutableStateOf<Node?>(null) }
    var showBugReport by remember { mutableStateOf(false) }

    SharedListScaffold(
        title = stringResource(Res.string.config),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        actions = {
            IconButton(onClick = { searchExpanded = !searchExpanded }) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = configLabels.searchLabel,
                )
            }
            IconButton(onClick = { onOpenNodeEdit(0) }) {
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
                modifier = Modifier.weight(1f),
                labels = configLabels,
                listState = listState,
                listContentPadding = PaddingValues(bottom = FloatingNavContentClearance),
                nestedScrollConnection = hideNavOnScroll,
                nodeDelayMap = configState.nodeDelayMap,
                onNodeSelected = { node ->
                    component.onSelectNode(node.id)
                    onNodeSelectedNavigateHome()
                },
                onEmptyAddClick = { onOpenNodeEdit(0) },
                onEditNode = { node -> onOpenNodeEdit(node.id) },
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
    hideNavOnScroll: NestedScrollConnection,
) {
    val homeLabels = rememberHomeUiLabels()
    val platformHooks = LocalPlatformRootHooks.current
    val homeNavClearance =
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
            FloatingNavBarHeight +
            FloatingNavBottomMargin
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
                    .padding(bottom = homeNavClearance)
                    .nestedScroll(hideNavOnScroll),
        )
    }
}

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

    SharedListScaffold(
        title = stringResource(Res.string.settings_title),
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = settingsLabels.cancelLabel,
                )
            }
        },
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
