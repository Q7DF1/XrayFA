package com.android.xrayfa.ui.component

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.android.xrayfa.R
import com.android.xrayfa.ui.QRCodeActivity
import com.android.xrayfa.ui.navigation.Config
import com.android.xrayfa.ui.navigation.Detail
import com.android.xrayfa.ui.navigation.Home
import com.android.xrayfa.ui.navigation.NavigateDestination
import com.android.xrayfa.ui.navigation.Subscription
import com.android.xrayfa.viewmodel.XrayViewmodel
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(
    xrayViewmodel: XrayViewmodel,
    onNavigate: (NavigateDestination) -> Unit
) {
    val nodes by xrayViewmodel.nodes.collectAsState()
    val qrBitMap by xrayViewmodel.qrBitmap.collectAsState()
    val deleteDialog by xrayViewmodel.deleteDialog.collectAsState()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var showSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    val scanOptions = ScanOptions()
    scanOptions.setOrientationLocked(true)
    scanOptions.captureActivity = QRCodeActivity::class.java
    scanOptions.setBeepEnabled(false)
    val barcodeLauncher = rememberLauncherForActivityResult(ScanContract()) {
            result->
        if (result.contents == null) {
            Toast.makeText(context, "Cancelled", Toast.LENGTH_LONG).show();
        }else {
            xrayViewmodel.addLink(result.contents)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize()){

            TopAppBar(
                title = {Text(stringResource(Config.title))},
                navigationIcon = {
                    Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = ""
                    )
                },
                actions = {ConfigActionButton(xrayViewmodel,onNavigate)},
                modifier = Modifier.shadow(4.dp)
            )
            if (nodes.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    Text(
                        style = MaterialTheme.typography.headlineLarge,
                        text = stringResource(R.string.no_configuration),
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }else {
                LazyColumn(
                    state = listState,
                ) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    items(nodes, key = {it.id}) {node ->
                        NodeCard(
                            node = node,
                            modifier = Modifier.padding(bottom = 8.dp),
                            delete = {
                                xrayViewmodel.showDeleteDialog(node.id)
                            },
                            onChoose = {
                                xrayViewmodel.setSelectedNode(node.id)
                                onNavigate(Home)
                            },
                            onShare = {
                                xrayViewmodel.generateQRCode(node.id)
                            },
                            onEdit = { view,x,y,width,height ->
                                //xrayViewmodel.startDetailActivity(context = context,id = node.id,x,y,width,height,view)
                                onNavigate(Detail(node.protocolPrefix,node.url))
                            },
                            selected =node.selected,
                            countryEmoji = node.countryISO
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = !listState.isAtBottom,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align (BiasAlignment(1f,0.9f))
        ) {
            FloatingActionButton(
                onClick = {showSheet = true},
                modifier = Modifier.padding(end = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "add config"
                )
            }
        }

        if (showSheet) {
            ModalBottomSheet(
                onDismissRequest = {showSheet = false},
                containerColor = MaterialTheme.colorScheme.surface,
                sheetState = sheetState
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) {
                                xrayViewmodel.addV2rayConfigFromClipboard(context)
                                showSheet = false
                            }
                            .clip(CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = ""
                        )
                        Text(
                            text = stringResource(R.string.clipboard_import),
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) {
                                barcodeLauncher.launch(scanOptions)
                                showSheet = false
                            }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = ""
                        )
                        Text(
                            text = stringResource(R.string.qrcode_import)
                        )
                    }
                }
            }
        }

        qrBitMap?.let {
            Dialog(onDismissRequest = { xrayViewmodel.dismissDialog() }) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    tonalElevation = 8.dp,
                    modifier = Modifier.padding(16.dp),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Image(
                            bitmap = qrBitMap!!.asImageBitmap(),
                            contentDescription = "qrcode",
                            modifier = Modifier.size(200.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                xrayViewmodel.exportConfigToClipboard(context)
                                xrayViewmodel.dismissDialog()
                            }
                        ) {
                            Text(
                                text = stringResource(R.string.clipboard_export)
                            )
                        }
                    }
                }
            }
        }
        if (deleteDialog) {
            DeleteDialog(
                onDismissRequest = {xrayViewmodel.hideDeleteDialog()},
            ) {
                xrayViewmodel.deleteNodeFromDialog()
            }
        }
    }

}


val LazyListState.isAtBottom: Boolean
    get() {
        val layoutInfo = layoutInfo
        val visibleItems = layoutInfo.visibleItemsInfo
        val totalItems = layoutInfo.totalItemsCount

        if (visibleItems.isEmpty() || totalItems == 0) return false

        val contentHeight = layoutInfo.totalItemsCount.takeIf { it > 0 }?.let {
            layoutInfo.visibleItemsInfo.sumOf { it.size }
        } ?: 0
        val viewportHeight = layoutInfo.viewportEndOffset

        if (contentHeight <= viewportHeight) return false

        val lastVisible = visibleItems.last()
        return lastVisible.index == totalItems - 1 &&
                lastVisible.offset + lastVisible.size <= viewportHeight
    }