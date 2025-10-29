package com.android.xrayfa.ui.component

import android.annotation.SuppressLint
import android.util.Log
import android.view.Surface
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat.getString
import com.android.xrayfa.R
import com.android.xrayfa.ui.QRCodeActivity
import com.android.xrayfa.viewmodel.XrayViewmodel
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(
    onNavigate2Home: (Int) -> Unit,
    xrayViewmodel: XrayViewmodel
) {
    val nodes by xrayViewmodel.getAllNodes().collectAsState(emptyList())
    val qrBitMap by xrayViewmodel.qrBitmap.collectAsState()
    val deleteDialog by xrayViewmodel.deleteDialog.collectAsState()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var showSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    val scanOptions = ScanOptions()
    scanOptions.setOrientationLocked(true)
    scanOptions.captureActivity = QRCodeActivity::class.java
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
            .padding(16.dp)

    ) {
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
                state = listState
            ) {
                items(nodes, key = {it.id}) {node ->
                    NodeCard(
                        node = node,
                        modifier = Modifier,
                        delete = {
                            xrayViewmodel.showDeleteDialog(node.id)
                        },
                        onChoose = {
                            xrayViewmodel.setSelectedNode(node.id)
                            onNavigate2Home(node.id)
                        },
                        onShare = {
                            xrayViewmodel.generateQRCode(node.id)
                        },
                        onEdit = {
                            xrayViewmodel.startDetailActivity(context = context,id = node.id)
                        },
                        selected =node.selected
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
        AnimatedVisibility(
            visible = !listState.isAtBottom,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align (BiasAlignment(1f,0.9f))
        ) {
//            AddConfigButton(
//                xrayViewmodel = xrayViewmodel,
//                modifier = Modifier.align(BiasAlignment(1f,0.8f))
//            )
            FloatingActionButton(
                onClick = {showSheet = true},
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
                sheetState = sheetState
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                            .clickable {
                                xrayViewmodel.addV2rayConfigFromClipboard(context)
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
                            .clickable {
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
            Dialog(onDismissRequest = {xrayViewmodel.hideDeleteDialog()}) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.confirm_delete),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        Text(
                            text = stringResource(R.string.delete_notify),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 24.dp)
                        )

                        // 按钮行
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = {xrayViewmodel.hideDeleteDialog()}) {
                                Text(stringResource(R.string.cancel))
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Button(
                                onClick = {
                                    xrayViewmodel.deleteLinkByIdWithDialog()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text(stringResource(R.string.delete), color = Color.White)
                            }
                        }
                    }
                }
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