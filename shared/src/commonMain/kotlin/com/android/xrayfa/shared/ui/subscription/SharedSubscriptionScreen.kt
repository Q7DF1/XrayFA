package com.android.xrayfa.shared.ui.subscription

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.xrayfa.model.Node
import com.android.xrayfa.model.Subscription
import com.android.xrayfa.shared.navigation.EmptySubscription
import com.android.xrayfa.shared.navigation.SubscriptionComponent
import com.android.xrayfa.shared.ui.chrome.SharedListScaffold
import com.android.xrayfa.shared.ui.widgets.SharedModalBottomSheet
import com.android.xrayfa.shared.ui.widgets.SharedOptionPickerField
import com.android.xrayfa.shared.platform.ClipboardWriter
import com.android.xrayfa.shared.resources.Res
import com.android.xrayfa.shared.resources.cancel
import com.android.xrayfa.shared.subscription.validateSubscriptionUrl
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import org.jetbrains.compose.resources.stringResource
import org.koin.mp.KoinPlatform

@Composable
fun SharedSubscriptionScreen(
    component: SubscriptionComponent,
    onBack: () -> Unit,
    onSubscriptionApplied: (Int) -> Unit,
    modifier: Modifier = Modifier,
    labels: SubscriptionUiLabels = SubscriptionUiLabels(),
    onScanQr: (() -> Unit)? = null,
) {
    val state by component.state.subscribeAsState()
    val clipboardWriter = remember { KoinPlatform.getKoin().get<ClipboardWriter>() }
    var copiedMessage by remember { mutableStateOf<String?>(null) }

    SharedListScaffold(
        title = labels.title,
        modifier = modifier,
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(Res.string.cancel),
                )
            }
        },
        actions = {
            if (onScanQr != null) {
                IconButton(onClick = onScanQr) {
                    Icon(Icons.Filled.QrCode, contentDescription = labels.scanQr)
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = component::openAddSheet) {
                Icon(Icons.Default.Add, contentDescription = labels.addSubscription)
            }
        },
    ) { innerPadding ->
        Box(
            modifier =
                Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (state.requesting) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                if (state.subscriptions.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Outlined.Link,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.outline,
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = labels.noSubscriptions,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(items = state.subscriptions, key = { it.id }) { item ->
                            SharedSubscriptionCard(
                                subscription = item,
                                onRefresh = {
                                    component.refreshSubscription(item) { id ->
                                        onSubscriptionApplied(id)
                                    }
                                },
                                onEdit = { component.openEditSheet(item.id) },
                                onDelete = { component.showDeleteDialog(item) },
                                onShareUrl = {
                                    clipboardWriter.writeText(item.url)
                                    copiedMessage = labels.subscriptionUrlCopied
                                },
                                shareUrlLabel = labels.shareSubscriptionUrl,
                            )
                        }
                    }
                }
            }

            if (state.subscribeError) {
                Surface(
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Text(
                        text = labels.subscribeFailed,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }

            copiedMessage?.let { message ->
                LaunchedEffect(message) {
                    kotlinx.coroutines.delay(2000)
                    copiedMessage = null
                }
                Surface(
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.inverseSurface,
                ) {
                    Text(
                        text = message,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                    )
                }
            }
        }
    }

    state.sheetSubscription?.let { sheetSubscription ->
        SharedSubscriptionEditSheet(
            subscription = sheetSubscription,
            nodes = state.allNodes,
            labels = labels,
            isMarkDuplicate = { mark ->
                component.isMarkDuplicate(mark, sheetSubscription.id)
            },
            onDismiss = component::closeSheet,
            onConfirm = { updated ->
                component.addOrUpdateSubscription(updated) { id ->
                    onSubscriptionApplied(id)
                }
            },
        )
    }

    state.deleteTarget?.let {
        AlertDialog(
            onDismissRequest = component::dismissDeleteDialog,
            title = { Text(labels.delete) },
            text = { Text(it.mark.ifEmpty { it.url }) },
            confirmButton = {
                TextButton(onClick = component::confirmDelete) {
                    Text(labels.delete)
                }
            },
            dismissButton = {
                TextButton(onClick = component::dismissDeleteDialog) {
                    Text(labels.cancel)
                }
            },
        )
    }
}

@Composable
private fun SharedSubscriptionCard(
    subscription: Subscription,
    onRefresh: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onShareUrl: () -> Unit,
    shareUrlLabel: String,
) {
    OutlinedCard(
        onClick = onRefresh,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors =
            CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        border =
            CardDefaults.outlinedCardBorder(enabled = true).copy(
                brush = SolidColor(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            ),
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = subscription.mark.ifEmpty { subscription.url },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            supportingContent = {
                Text(
                    text = subscription.url,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.outline,
                )
            },
            leadingContent = {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    shape = CircleShape,
                    modifier = Modifier.size(36.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Link,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            },
            trailingContent = {
                Row {
                    IconButton(onClick = onShareUrl) {
                        Icon(
                            Icons.Outlined.ContentCopy,
                            contentDescription = shareUrlLabel,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}

@Composable
private fun SharedSubscriptionEditSheet(
    subscription: Subscription,
    nodes: List<Node>,
    labels: SubscriptionUiLabels,
    isMarkDuplicate: (String) -> Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Subscription) -> Unit,
) {
    var nickName by remember(subscription) { mutableStateOf(subscription.mark) }
    var url by remember(subscription) { mutableStateOf(subscription.url) }
    var preNodeId by remember(subscription) { mutableStateOf(subscription.preNodeId) }
    var nextNodeId by remember(subscription) { mutableStateOf(subscription.nextNodeId) }
    var nickNameIsNull by remember { mutableStateOf(false) }
    var nickNameIsDuplicate by remember { mutableStateOf(false) }
    var urlIsInvalid by remember { mutableStateOf(false) }

    LaunchedEffect(subscription, nickName) {
        val resolved = nickName.trim()
        nickNameIsNull = resolved.isBlank()
        nickNameIsDuplicate = isMarkDuplicate(resolved)
    }

    SharedModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text =
                    if (subscription.id <= 0) {
                        labels.addSubscription
                    } else {
                        labels.editSubscription
                    },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            OutlinedTextField(
                value = nickName,
                onValueChange = {
                    nickName = it
                    val resolved = it.trim()
                    nickNameIsNull = resolved.isBlank()
                    nickNameIsDuplicate = isMarkDuplicate(resolved)
                },
                label = { Text(labels.nickName) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                isError = nickNameIsNull || nickNameIsDuplicate,
                supportingText =
                    if (nickNameIsDuplicate) {
                        { Text(labels.duplicateMarkError) }
                    } else {
                        null
                    },
                shape = RoundedCornerShape(12.dp),
            )

            OutlinedTextField(
                value = url,
                onValueChange = {
                    url = it
                    urlIsInvalid = !validateSubscriptionUrl(it)
                },
                label = { Text(labels.subscriptionUrl) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                isError = urlIsInvalid,
                shape = RoundedCornerShape(12.dp),
            )

            SharedSubscriptionNodeSelector(
                label = labels.preNode,
                selectedNodeId = preNodeId,
                nodes = nodes,
                noneLabel = labels.none,
                onNodeSelected = { preNodeId = it },
            )

            SharedSubscriptionNodeSelector(
                label = labels.nextNode,
                selectedNodeId = nextNodeId,
                nodes = nodes,
                noneLabel = labels.none,
                onNodeSelected = { nextNodeId = it },
            )

            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(labels.cancel)
                }
                Spacer(Modifier.size(8.dp))
                Button(
                    onClick = {
                        val resolvedMark = nickName.trim()
                        nickNameIsNull = resolvedMark.isBlank()
                        nickNameIsDuplicate = isMarkDuplicate(resolvedMark)
                        urlIsInvalid = !validateSubscriptionUrl(url)
                        if (nickNameIsNull || nickNameIsDuplicate || urlIsInvalid) {
                            return@Button
                        }
                        onConfirm(
                            Subscription(
                                id = subscription.id,
                                mark = resolvedMark,
                                url = url.trim(),
                                preNodeId = preNodeId,
                                nextNodeId = nextNodeId,
                                isAutoUpdate = subscription.isAutoUpdate,
                            ),
                        )
                    },
                    enabled = !urlIsInvalid && !nickNameIsNull && !nickNameIsDuplicate,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(labels.confirm)
                }
            }
        }
    }
}

@Composable
private fun SharedSubscriptionNodeSelector(
    label: String,
    selectedNodeId: Int,
    nodes: List<Node>,
    noneLabel: String,
    onNodeSelected: (Int) -> Unit,
) {
    val selectedNode = nodes.find { it.id == selectedNodeId }
    val options =
        buildList {
            add(noneLabel to -1)
            nodes.forEach { node ->
                add((node.remark ?: node.address) to node.id)
            }
        }
    SharedOptionPickerField(
        valueLabel = selectedNode?.remark ?: selectedNode?.address ?: noneLabel,
        label = label,
        options = options,
        onSelected = onNodeSelected,
    )
}
