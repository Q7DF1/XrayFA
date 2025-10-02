package com.android.xrayfa.ui.navigation

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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat.getString
import com.android.xrayfa.ui.QRCodeActivity
import com.android.xrayfa.ui.component.NodeCard
import com.android.xrayfa.viewmodel.XrayViewmodel
import com.android.xrayfa.R
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

data object Config: NavigateDestination {
    override val icon: ImageVector
        get() = Icons.Default.Build
    override val route: String
        get() = "config"
    override val containerColor: Color
        get() = Color.White
}


@Composable
fun ConfigScreen(
    onNavigate2Home: (Int) -> Unit,
    xrayViewmodel: XrayViewmodel
) {
    val nodes by xrayViewmodel.getAllNodes().collectAsState(emptyList())
    val qrBitMap by xrayViewmodel.qrBitmap.collectAsState()
    val context = LocalContext.current
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
            ) {

                items(nodes, key = {it.id}) {node ->
                    NodeCard(
                        node = node,
                        modifier = Modifier,
                        delete = {
                            xrayViewmodel.deleteLinkById(node.id)
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
        AddConfigButton(
            xrayViewmodel = xrayViewmodel,
            modifier = Modifier.align(BiasAlignment(1f,0.8f))
        )


        qrBitMap?.let {
            Dialog(onDismissRequest = { xrayViewmodel.dismissDialog() }) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    tonalElevation = 8.dp,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Image(bitmap = qrBitMap!!.asImageBitmap(), contentDescription = "qrcode", modifier = Modifier.size(200.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { xrayViewmodel.dismissDialog() }) {
                            Text(
                                text = stringResource(R.string.close_window)
                            )
                        }
                    }
                }
            }
        }
    }

}


@Composable
fun AddConfigButton(
    xrayViewmodel: XrayViewmodel,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var toggle by remember { mutableStateOf(false) }
    val context = LocalContext.current
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
        contentAlignment = Alignment.BottomEnd,
        modifier = modifier.fillMaxSize()
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.End,
        ) {
            AnimatedVisibility(
                visible = expanded,
                enter = scaleIn(
                    initialScale = 0.8f,
                    animationSpec = tween(250)
                ) + fadeIn(),
                exit = scaleOut(
                    targetScale = 0.8f,
                    animationSpec = tween(250)
                ) + fadeOut()
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    modifier = Modifier.border(
                        width = 2.dp,
                        color = Color.Gray,
                        shape = RoundedCornerShape(8.dp)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        verticalArrangement = Arrangement.Bottom,
                        horizontalAlignment = Alignment.Start,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        PopUpButton(
                            icon = Icons.Default.Edit,
                            text = stringResource(R.string.clipboard_import),
                            onClick = {
                                xrayViewmodel.addV2rayConfigFromClipboard(context)
                            }
                        )
                        HorizontalDivider(
                            color = Color.Gray,
                            thickness = 1.dp,
                            modifier = Modifier.width(200.dp)
                                .padding(horizontal = 16.dp)
                        )
                        PopUpButton(
                            icon = Icons.Default.Build,
                            text = stringResource(R.string.qrcode_import),
                            onClick = {barcodeLauncher.launch(scanOptions)}
                        )
                        HorizontalDivider(
                            color = Color.Gray,
                            thickness = 1.dp,
                            modifier = Modifier.width(200.dp)
                                .padding(horizontal = 16.dp)
                        )
                        PopUpButton(
                            icon = Icons.Default.Build,
                            text = stringResource(R.string.qrcode_import),
                            onClick = {barcodeLauncher.launch(scanOptions)}
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            FloatingActionButton(
                onClick = {
                    expanded = !expanded
                }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.Done else Icons.Default.Add,
                        contentDescription = ""
                    )

                    AnimatedVisibility(visible = toggle) {
                        Text(
                            text = getString(LocalContext.current,R.string.add_config),
                            modifier = Modifier.padding(start = 8.dp, end = 3.dp)
                        )
                    }
                }
            }
        } //end of Row

    }
}


@Composable
fun PopUpButton(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit = {},
) {
    Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                .clickable(
                    onClick = onClick
                ),
            verticalAlignment = Alignment.CenterVertically
    ) {
            Icon(icon, contentDescription = "Edit")
            Text(
                text = text,
                modifier = Modifier.padding(start = 8.dp, end = 3.dp),
                style = MaterialTheme.typography.bodyMedium
            )
    }
}