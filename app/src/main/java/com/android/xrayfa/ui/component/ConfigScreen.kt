package com.android.xrayfa.ui.component

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Popup
import androidx.compose.ui.zIndex
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ConfigScreen(
    xrayViewmodel: XrayViewmodel,
    bottomPadding: Dp = 0.dp,
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

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(
        rememberTopAppBarState()
    )
    // Observe the overlap fraction to determine if the list is scrolled
    val isScrolled by remember {
        derivedStateOf { scrollBehavior.state.overlappedFraction > 0f }
    }

    // Animate the shadow elevation for a smooth transition
    val appBarElevation by animateDpAsState(
        targetValue = if (isScrolled) 4.dp else 0.dp,
        label = "TopBarShadowElevation"
    )
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
            Surface(
                shadowElevation = appBarElevation,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.zIndex(1f)
            ) {
                TopAppBar(
                    title = {Text(stringResource(Config.title))},
                    navigationIcon = {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = ""
                        )
                    },
                    actions = {ConfigActionButton(xrayViewmodel,onNavigate)},
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    scrollBehavior = scrollBehavior,
                )
            }
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
                    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
                ) {
                    items(nodes, key = {it.id}) {node ->
                        NodeCard(
                            node = node,
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
                            roundCorner = false,
                            countryEmoji = node.countryISO
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = !listState.isAtBottom{ isAtBottom ->
                if (isAtBottom) xrayViewmodel.hideNavigationBar() else xrayViewmodel.showNavigationBar()
            },
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align (BiasAlignment(1f,0.9f))
                .padding(bottom = bottomPadding)
        ) {
            var checked by remember { mutableStateOf(false) }

            Box(
                modifier = Modifier.padding(end = 8.dp)
            ) {
                SplitButtonLayout(
                    leadingButton = {
                        SplitButtonDefaults.LeadingButton(onClick = { /* TODO */ }) {
                            Icon(
                                Icons.Filled.Edit,
                                modifier = Modifier.size(SplitButtonDefaults.LeadingIconSize),
                                contentDescription = "Localized description",
                            )
                            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                            Text("Add")
                        }
                    },
                    trailingButton = {
                        val description = "Toggle Button"
                        // Icon-only trailing button should have a tooltip for a11y.
                        TooltipBox(
                            positionProvider =
                                TooltipDefaults.rememberTooltipPositionProvider(
                                    TooltipAnchorPosition.Above
                                ),
                            tooltip = { PlainTooltip { Text(description) } },
                            state = rememberTooltipState(),
                        ) {
                            SplitButtonDefaults.TrailingButton(
                                checked = checked,
                                onCheckedChange = { checked = it },
                                modifier =
                                    Modifier.semantics {
                                        stateDescription = if (checked) "Expanded" else "Collapsed"
                                        contentDescription = description
                                    },
                            ) {
                                val rotation: Float by
                                animateFloatAsState(
                                    targetValue = if (checked) 180f else 0f,
                                    label = "Trailing Icon Rotation",
                                )
                                Icon(
                                    Icons.Filled.KeyboardArrowDown,
                                    modifier =
                                        Modifier.size(SplitButtonDefaults.TrailingIconSize).graphicsLayer {
                                            this.rotationZ = rotation
                                        },
                                    contentDescription = "Localized description",
                                )
                            }
                        }
                    },
                )

                DropdownMenu(
                    expanded = checked,
                    onDismissRequest = { checked = false },
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    DropdownMenuItem(
                        text = { Text("from clipboard") },
                        onClick = {
                            xrayViewmodel.addV2rayConfigFromClipboard(context)
                            checked = false
                        },
                        leadingIcon = { Icon(Icons.Outlined.ContentCut, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("from QR code") },
                        onClick = {
                            barcodeLauncher.launch(scanOptions)
                            checked = false
                        },
                        leadingIcon = { Icon(Icons.Outlined.QrCodeScanner, contentDescription = null) },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("stay in beta") },
                        onClick = { /* Handle send feedback! */ },
                        leadingIcon = { Icon(Icons.Outlined.Email, contentDescription = null) },
                        trailingIcon = { Text("F11", textAlign = TextAlign.Center) },
                    )
                }
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


fun LazyListState.isAtBottom(callBack: (Boolean)-> Unit): Boolean{
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
    val isAtBottom =  lastVisible.index == totalItems - 1 &&
            lastVisible.offset + lastVisible.size <= viewportHeight
    callBack(isAtBottom)
    return isAtBottom
}