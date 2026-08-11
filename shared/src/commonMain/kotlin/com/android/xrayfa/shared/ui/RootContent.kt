package com.android.xrayfa.shared.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.xrayfa.shared.navigation.ConfigComponent
import com.android.xrayfa.shared.navigation.NodeEditTarget
import com.android.xrayfa.shared.navigation.RootComponent
import com.android.xrayfa.shared.navigation.RootTab
import com.android.xrayfa.shared.navigation.SettingsComponent
import com.android.xrayfa.shared.navigation.rememberSubscriptionComponent
import com.android.xrayfa.shared.ui.config.SharedConfigImportMenu
import com.android.xrayfa.shared.ui.config.SharedConfigSection
import com.android.xrayfa.shared.ui.config.SharedEditScreen
import com.android.xrayfa.shared.ui.config.ConfigUiLabels
import com.android.xrayfa.shared.ui.config.EditUiLabels
import com.android.xrayfa.shared.config.NodeFormEditor
import com.android.xrayfa.shared.ui.home.HomeTopBar
import com.android.xrayfa.shared.ui.nav.XrayFloatingNav
import com.android.xrayfa.shared.ui.qr.SharedQrScannerScreen
import com.android.xrayfa.shared.ui.settings.SettingsUiLabels
import com.android.xrayfa.shared.ui.settings.SharedAppsInfoScreen
import com.android.xrayfa.shared.ui.settings.RouteSettingsUiLabels
import com.android.xrayfa.shared.ui.settings.SharedAppLogScreen
import com.android.xrayfa.shared.ui.settings.SharedRouteSettingsScreen
import com.android.xrayfa.shared.ui.settings.SharedSettingsAboutSection
import com.android.xrayfa.shared.ui.settings.SharedSettingsGeneralSection
import com.android.xrayfa.shared.ui.settings.SharedSettingsPlatformSection
import com.android.xrayfa.shared.ui.settings.SharedSettingsSubscriptionSection
import com.android.xrayfa.shared.ui.subscription.SharedSubscriptionScreen
import com.arkivanov.decompose.extensions.compose.pages.ChildPages
import com.arkivanov.decompose.extensions.compose.pages.PagesScrollAnimation
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import org.koin.mp.KoinPlatform

@Composable
fun RootContent(
    component: RootComponent,
    modifier: Modifier = Modifier,
) {
    val pages by component.pages.subscribeAsState()
    val selectedTab = pages.items.getOrNull(pages.selectedIndex)?.configuration ?: RootTab.Home
    val showBottomNav = selectedTab != RootTab.Settings

    Box(modifier = modifier.fillMaxSize()) {
        ChildPages(
            modifier = Modifier.fillMaxSize(),
            pages = component.pages,
            onPageSelected = component::selectTab,
            scrollAnimation = PagesScrollAnimation.Default,
        ) { _, child ->
            when (child) {
                is RootComponent.Child.Home ->
                    HomeTabScreen(
                        component = child.component,
                        onSettingsClick = { component.selectTab(RootTab.Settings) },
                    )
                is RootComponent.Child.Config ->
                    ConfigTabScreen(
                        component = child.component,
                        onNodeSelectedNavigateHome = { component.selectTab(RootTab.Home) },
                    )
                is RootComponent.Child.Settings ->
                    SettingsTabScreen(
                        component = child.component,
                        onBack = { component.selectTab(RootTab.Home) },
                    )
            }
        }

        if (showBottomNav) {
            XrayFloatingNav(
                selectedTab =
                    when (selectedTab) {
                        RootTab.Config -> RootTab.Config
                        else -> RootTab.Home
                    },
                onTabSelected = component::selectTab,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(bottom = 16.dp, start = 16.dp, end = 16.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigTabScreen(
    component: ConfigComponent,
    onNodeSelectedNavigateHome: () -> Unit,
) {
    var showSubscriptions by remember { mutableStateOf(false) }
    var showQrScanner by remember { mutableStateOf(false) }
    val subscriptionComponent = rememberSubscriptionComponent()
    val settingsLabels = remember { SettingsUiLabels() }
    val configLabels = remember { ConfigUiLabels() }
    val editLabels = remember { EditUiLabels() }
    val configState by component.state.subscribeAsState()

    configState.nodeEditTarget?.let { target ->
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

    if (showQrScanner) {
        SharedQrScannerScreen(
            onResult = { result ->
                component.onImportFromLink(result)
                showQrScanner = false
            },
            onBack = { showQrScanner = false },
            title = settingsLabels.qrScannerTitle,
            permissionRequiredMessage = settingsLabels.qrPermissionRequired,
        )
        return
    }

    if (showSubscriptions) {
        SharedSubscriptionScreen(
            component = subscriptionComponent,
            onBack = { showSubscriptions = false },
            onSubscriptionApplied = { subscriptionId ->
                component.onSelectFilter(subscriptionId)
                showSubscriptions = false
            },
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Config", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = component::onOpenCreateNode) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = configLabels.createConfigLabel,
                        )
                    }
                    SharedConfigImportMenu(
                        onImportFromClipboard = component::onImportFromClipboard,
                        onManageSubscriptions = { showSubscriptions = true },
                        onScanQr = { showQrScanner = true },
                        scanQrLabel = settingsLabels.scanQrLabel,
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
        SharedConfigSection(
            component = component,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(bottom = 88.dp),
            labels = configLabels,
            onNodeSelected = { node ->
                component.onSelectNode(node.id)
                onNodeSelectedNavigateHome()
            },
            onEmptyAddClick = component::onOpenCreateNode,
            onEditNode = { node -> component.onOpenEditNode(node.id) },
            onDeleteNode = component::onShowDeleteNode,
        )
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
}

@Composable
private fun HomeTabScreen(
    component: com.android.xrayfa.shared.navigation.HomeComponent,
    onSettingsClick: () -> Unit,
) {
    Scaffold(
        topBar = {
            HomeTopBar(
                title = "Home",
                onSettingsClick = onSettingsClick,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        SharedHomeSection(
            component = component,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(bottom = 88.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsTabScreen(
    component: SettingsComponent,
    onBack: () -> Unit,
) {
    var showAppLog by remember { mutableStateOf(false) }
    var showRouteSettings by remember { mutableStateOf(false) }
    var showAppsInfo by remember { mutableStateOf(false) }
    val settingsLabels = remember { SettingsUiLabels() }
    val routeSettingsLabels = remember { RouteSettingsUiLabels() }

    if (showAppsInfo) {
        SharedAppsInfoScreen(
            component = component,
            onBack = { showAppsInfo = false },
            labels = settingsLabels,
        )
        return
    }

    if (showRouteSettings) {
        SharedRouteSettingsScreen(
            component = component,
            labels = routeSettingsLabels,
            onBack = { showRouteSettings = false },
        )
        return
    }

    if (showAppLog) {
        SharedAppLogScreen(
            onBack = { showAppLog = false },
            labels = settingsLabels,
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
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
        androidx.compose.foundation.layout.Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            SharedSettingsGeneralSection(
                component = component,
                scrollEnabled = true,
                labels = settingsLabels,
            )
            SharedSettingsPlatformSection(
                labels = settingsLabels,
                onAppsClick = { showAppsInfo = true },
                onLogcatClick = { showAppLog = true },
                onRouteClick = { showRouteSettings = true },
            )
            SharedSettingsSubscriptionSection(component = component, labels = settingsLabels)
            SharedSettingsAboutSection(component = component, labels = settingsLabels)
        }
    }
}
