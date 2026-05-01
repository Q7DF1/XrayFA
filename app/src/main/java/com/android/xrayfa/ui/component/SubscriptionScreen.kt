package com.android.xrayfa.ui.component

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.android.xrayfa.R
import com.android.xrayfa.dto.Node
import com.android.xrayfa.dto.Subscription
import com.android.xrayfa.viewmodel.SubscriptionViewmodel
import java.net.URI

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionScreen(
    viewmodel: SubscriptionViewmodel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val subscriptions by viewmodel.subscriptions.collectAsState()
    var isBottomSheetShow by remember { mutableStateOf(false) }

    val subscription by viewmodel.selectSubscription.collectAsState()
    var nickName by remember(subscription) { mutableStateOf(subscription.mark) }
    var url by remember(subscription) { mutableStateOf(subscription.url) }
    var preNodeId by remember(subscription) { mutableStateOf(subscription.preNodeId) }
    var nextNodeId by remember(subscription) { mutableStateOf(subscription.nextNodeId) }
    var nickNameIsNull by remember { mutableStateOf(false) }
    var urlIsNullOrInvalid by remember { mutableStateOf(false) }

    val deleteDialog by viewmodel.deleteDialog.collectAsState()
    val requesting by viewmodel.requesting.collectAsState()
    val subscribeError by viewmodel.subscribeError.collectAsState()
    val qrBitMap by viewmodel.qrBitmap.collectAsState()
    val nodesFlow by viewmodel.nodes.collectAsState()
    val allNodes by nodesFlow.collectAsState(initial = emptyList())

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(
        rememberTopAppBarState()
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.menu_subscription), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "back")
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    viewmodel.setSelectSubscriptionEmpty()
                    isBottomSheetShow = true
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Icon(Icons.Default.Add, "add subscription")
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            Column {
                if (requesting) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primaryContainer
                    )
                }

                if (subscriptions.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Outlined.Link,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = "No Subscriptions",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(items = subscriptions, key = { it.id }) { item ->
                            OutlinedCard(
                                onClick = {
                                    viewmodel.getSubscriptionWithCallback(item.url, item.id) {
                                        onBack()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.outlinedCardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                border = CardDefaults.outlinedCardBorder(enabled = true).copy(
                                    brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                )
                            ) {
                                ListItem(
                                    headlineContent = {
                                        Text(
                                            text = item.mark,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    },
                                    supportingContent = {
                                        Text(
                                            text = item.url,
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    },
                                    leadingContent = {
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                            shape = CircleShape,
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Link,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    },
                                    trailingContent = {
                                        Row {
                                            IconButton(onClick = {
                                                viewmodel.getSubscriptionByIdWithCallback(item.id) {
                                                    isBottomSheetShow = true
                                                }
                                            }) {
                                                Icon(Icons.Outlined.Edit, "edit", modifier = Modifier.size(20.dp))
                                            }
                                            IconButton(onClick = {
                                                viewmodel.generateQRCode(id = item.id)
                                            }) {
                                                Icon(Icons.Outlined.Share, "share", modifier = Modifier.size(20.dp))
                                            }
                                            IconButton(onClick = {
                                                viewmodel.showDeleteDialog(item)
                                            }) {
                                                Icon(
                                                    Icons.Outlined.Delete,
                                                    "delete",
                                                    modifier = Modifier.size(20.dp),
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                    },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                )
                            }
                        }
                    }
                }
            }

            if (isBottomSheetShow) {
                ModalBottomSheet(
                    onDismissRequest = { isBottomSheetShow = false },
                    containerColor = MaterialTheme.colorScheme.surface,
                    dragHandle = { BottomSheetDefaults.DragHandle() }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .padding(bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = if (subscription.id <= 0) "Add Subscription" else "Edit Subscription",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )

                        OutlinedTextField(
                            value = nickName,
                            onValueChange = {
                                nickName = it
                                nickNameIsNull = nickName.isBlank()
                            },
                            label = { Text(stringResource(R.string.nick_name)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            isError = nickNameIsNull,
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = url,
                            onValueChange = {
                                url = it
                                urlIsNullOrInvalid = !validateUrl(url) // Fix: Invert here to correctly mark error
                            },
                            label = { Text(stringResource(R.string.subscription_url)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            isError = urlIsNullOrInvalid,
                            shape = RoundedCornerShape(12.dp)
                        )

                        NodeSelector(
                            label = "Pre Node",
                            selectedNodeId = preNodeId,
                            nodes = allNodes,
                            onNodeSelected = { preNodeId = it }
                        )

                        NodeSelector(
                            label = "Next Node",
                            selectedNodeId = nextNodeId,
                            nodes = allNodes,
                            onNodeSelected = { nextNodeId = it }
                        )

                        Row(
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { isBottomSheetShow = false }) {
                                Text(stringResource(R.string.cancel))
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    validateThenConfirm(nickName, url) {
                                        viewmodel.addOrUpdateSubscription(
                                            subscription = Subscription(
                                                id = subscription.id,
                                                mark = nickName,
                                                url = url,
                                                preNodeId = preNodeId,
                                                nextNodeId = nextNodeId,
                                                isAutoUpdate = subscription.isAutoUpdate
                                            )
                                        )
                                    }.also {
                                        nickNameIsNull = it.first
                                        urlIsNullOrInvalid = it.second
                                    }
                                    isBottomSheetShow = false
                                },
                                enabled = !urlIsNullOrInvalid && !nickNameIsNull,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(stringResource(R.string.confirm))
                            }
                        }
                    }
                }
            }

            qrBitMap?.let {
                Dialog(onDismissRequest = { viewmodel.dismissQRCode() }) {
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        tonalElevation = 6.dp,
                        modifier = Modifier.padding(16.dp),
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Text(
                                "Share Subscription",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(24.dp))
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color.White,
                                modifier = Modifier.size(220.dp).padding(8.dp)
                            ) {
                                Image(
                                    bitmap = qrBitMap!!.asImageBitmap(),
                                    contentDescription = "qrcode",
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Spacer(Modifier.height(24.dp))

                            Button(
                                onClick = {
                                    viewmodel.exportConfigToClipboard(context)
                                    viewmodel.dismissQRCode()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(text = stringResource(R.string.clipboard_export))
                            }
                        }
                    }
                }
            }

            if (deleteDialog) {
                DeleteDialog(
                    onDismissRequest = { viewmodel.dismissDeleteDialog() },
                ) {
                    viewmodel.deleteSubscriptionWithDialog()
                }
            }
            ExceptionMessage(subscribeError, stringResource(R.string.subscribe_failed))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodeSelector(
    label: String,
    selectedNodeId: Int,
    nodes: List<Node>,
    onNodeSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedNode = nodes.find { it.id == selectedNodeId }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selectedNode?.remark ?: selectedNode?.address ?: "None",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("None") },
                onClick = {
                    onNodeSelected(-1)
                    expanded = false
                }
            )
            nodes.forEach { node ->
                DropdownMenuItem(
                    text = { Text(node.remark ?: node.address) },
                    onClick = {
                        onNodeSelected(node.id)
                        expanded = false
                    }
                )
            }
        }
    }
}

const val TAG = "SubscriptionScreen"
fun validateUrl(url: String): Boolean {
    if (url.isBlank()) return false

    return try {
        val uri = URI(url.trim())
        val scheme = uri.scheme?.lowercase()
        // Simple check: must have a scheme and a host
        (scheme == "http" || scheme == "https") && !uri.host.isNullOrBlank()
    } catch (e: Exception) {
        false
    }
}

fun validateThenConfirm(nickName: String, url: String, onConfirm: () -> Unit): Pair<Boolean, Boolean> {
    val isNickNameNull = nickName.isBlank()
    val isUrlIllegal = !validateUrl(url)
    if (!isNickNameNull && !isUrlIllegal) {
        onConfirm()
    }
    return isNickNameNull to isUrlIllegal
}
