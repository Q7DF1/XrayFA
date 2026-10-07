package com.android.xrayfa.shared.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import com.android.xrayfa.model.Node
import com.android.xrayfa.model.isJsonConfig
import com.android.xrayfa.shared.ui.config.jsonConfigErrorText
import com.android.xrayfa.shared.navigation.ConfigComponent
import com.android.xrayfa.shared.resources.*
import com.android.xrayfa.shared.ui.chrome.SharedListScaffold
import com.android.xrayfa.shared.ui.config.ConfigTabChromeState
import com.android.xrayfa.shared.ui.config.SharedConfigFilterBar
import com.android.xrayfa.shared.ui.config.SharedConfigImportMenu
import com.android.xrayfa.shared.ui.config.SharedConfigSection
import com.android.xrayfa.shared.ui.config.shouldCommitOverlayScroll
import com.android.xrayfa.shared.ui.nav.rememberFloatingNavClearance
import com.android.xrayfa.shared.ui.platform.LocalPlatformRootHooks
import com.android.xrayfa.shared.ui.transitions.TransitionDestinations
import com.android.xrayfa.shared.ui.transitions.sharedContainer
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ConfigTabScreen(
    component: ConfigComponent,
    chromeState: ConfigTabChromeState,
    onNodeSelectedNavigateHome: () -> Unit,
    onOpenNodeEdit: (Int) -> Unit,
    onOpenSubscriptions: () -> Unit,
    onOpenQrScanner: () -> Unit,
    onOpenJsonConfig: (Int) -> Unit,
) {
    val platformHooks = LocalPlatformRootHooks.current
    val configLabels = rememberConfigUiLabels()
    val settingsLabels = rememberSettingsUiLabels()
    val configState by component.state.subscribeAsState()
    val configBottomClearance = rememberFloatingNavClearance()
    val scope = rememberCoroutineScope()
    var shareNode by remember { mutableStateOf<Node?>(null) }
    var showBugReport by remember { mutableStateOf(false) }

    LaunchedEffect(chromeState.pendingOverlayScroll, configState.searchQuery, configState.nodes) {
        val pending = chromeState.pendingOverlayScroll ?: return@LaunchedEffect
        if (!shouldCommitOverlayScroll(pending, configState.searchQuery, configState.nodes)) {
            return@LaunchedEffect
        }
        val index = configState.nodes.indexOfFirst { it.id == pending.nodeId }
        if (index >= 0) {
            chromeState.listState.animateScrollToItem(index)
        }
        chromeState.pendingOverlayScroll = null
    }

    SharedListScaffold(
        title = stringResource(Res.string.config),
        largeTitle = false,
        collapseTitleOnScroll = true,
        titleExpandKey = configState.selectedFilterId,
        titleCollapseState = chromeState.titleCollapseState,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        footerUnderBar = {
            SharedConfigFilterBar(
                component = component,
                trailingContent = {
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
        },
        actions = {
            IconButton(
                onClick = { onOpenNodeEdit(0) },
                modifier =
                    Modifier.sharedContainer(
                        destination = TransitionDestinations.NODE_EDIT_NEW,
                        shape = CircleShape,
                        containerColor = Color.Transparent,
                    ),
            ) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = configLabels.createConfigLabel,
                )
            }
            SharedConfigImportMenu(
                onImportFromClipboard = component::onImportFromClipboard,
                // 一个按钮开两个目的地（菜单里的订阅与扫码），key 不同，所以叠两层。
                modifier =
                    Modifier
                        .sharedContainer(
                            destination = TransitionDestinations.SUBSCRIPTIONS,
                            shape = CircleShape,
                            containerColor = Color.Transparent,
                        )
                        .sharedContainer(
                            destination = TransitionDestinations.QR,
                            shape = CircleShape,
                            containerColor = Color.Transparent,
                        ),
                onManageSubscriptions = onOpenSubscriptions,
                onScanQr = onOpenQrScanner,
                importFromClipboardLabel = stringResource(Res.string.clipboard_import),
                manageSubscriptionsLabel = stringResource(Res.string.menu_subscription),
                scanQrLabel = settingsLabels.scanQrLabel,
                additionalMenuItems = { dismiss ->
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.json_config_import)) },
                        leadingIcon = {
                            Icon(Icons.Outlined.UploadFile, contentDescription = null)
                        },
                        onClick = { dismiss(); onOpenJsonConfig(0) },
                    )
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
                                    chromeState.listState.animateScrollToItem(index)
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
        SharedConfigSection(
            component = component,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            labels = configLabels,
            listState = chromeState.listState,
            showFilterBar = false,
            listContentPadding = PaddingValues(bottom = configBottomClearance),
            nodeDelayMap = configState.nodeDelayMap,
            onNodeSelected = { node ->
                component.onSelectNode(node.id, onNodeSelectedNavigateHome)
            },
            rowModifier = { node ->
                Modifier.sharedContainer(
                    destination = TransitionDestinations.nodeEdit(node.id),
                    shape = RectangleShape,
                    containerColor = Color.Transparent,
                )
            },
            onEmptyAddClick = { onOpenNodeEdit(0) },
            onEditNode = { node -> if (node.isJsonConfig) onOpenJsonConfig(node.id) else onOpenNodeEdit(node.id) },
            onDeleteNode = component::onShowDeleteNode,
            onShareNode = { node -> shareNode = node },
        )
    }

    configState.configError?.let { reason ->
        AlertDialog(onDismissRequest = component::onDismissConfigError, text = { Text(jsonConfigErrorText(reason)) }, confirmButton = { TextButton(onClick = component::onDismissConfigError) { Text(configLabels.cancelLabel) } })
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
