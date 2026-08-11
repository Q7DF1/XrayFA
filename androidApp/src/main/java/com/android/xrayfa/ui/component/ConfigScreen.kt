package com.android.xrayfa.ui.component

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import com.android.xrayfa.R
import com.android.xrayfa.shared.ui.config.SharedConfigImportMenu
import com.android.xrayfa.shared.ui.config.SharedConfigSection
import com.android.xrayfa.ui.config.rememberAndroidConfigComponent
import com.android.xrayfa.ui.config.rememberConfigFilterLabels
import com.android.xrayfa.ui.config.rememberConfigUiLabels
import com.android.xrayfa.ui.navigation.Config
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.android.xrayfa.ui.navigation.Detail
import com.android.xrayfa.ui.navigation.Edit
import com.android.xrayfa.ui.navigation.Home
import com.android.xrayfa.ui.navigation.NavigateDestination
import com.android.xrayfa.ui.navigation.Subscription
import com.android.xrayfa.viewmodel.XrayViewmodel
import com.android.xrayfa.ui.component.BugReportDialog
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.foundation.background

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.ui.text.style.TextAlign
import com.android.xrayfa.ui.navigation.ScanQR
import com.android.xrayfa.viewmodel.XrayViewmodel.Companion.SUB_ALL
import com.android.xrayfa.viewmodel.XrayViewmodel.Companion.SUB_MANUAL

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ConfigScreen(
    xrayViewmodel: XrayViewmodel,
    bottomPadding: Dp = 0.dp,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onNavigate: (NavigateDestination) -> Unit
) {
    val configComponent =
        rememberAndroidConfigComponent(
            filterLabels = rememberConfigFilterLabels(),
        )
    val configState by configComponent.state.subscribeAsState()
    val queryNodes by xrayViewmodel.queryNodes.collectAsState()
    val qrBitMap by xrayViewmodel.qrBitmap.collectAsState()
    val deleteDialog by xrayViewmodel.deleteDialog.collectAsState()
    val bugReportDialog by xrayViewmodel.bugReportDialog.collectAsState()
    val nodeDelayMap by xrayViewmodel.nodeDelayMap.collectAsState()
    val isTestingAll by xrayViewmodel.isTestingAll.collectAsState()

    val configLabels = rememberConfigUiLabels()

    LaunchedEffect(configState.selectedFilterId) {
        xrayViewmodel.selectSubscription(configState.selectedFilterId)
    }

    val context = LocalContext.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
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

    suspend fun scrollToItemById(id: Int) {
        val index = configState.nodes.indexOfFirst { it.id == id }
        if (index != -1) {
            listState.animateScrollToItem(index)
        }
    }

    suspend fun scrollToSelected() {
        val index = configState.nodes.indexOfFirst { it.selected }
        if (index != -1) {
            listState.animateScrollToItem(index)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()){
            Surface(
                shadowElevation = appBarElevation,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.zIndex(1f)
            ) {
                Column {
                    TopAppBar(
                        title = {
                            Text(stringResource(Config.title), fontWeight = FontWeight.Bold)
                        },
                        actions = {
                            IconButton(onClick = { onNavigate(Edit) }) {
                                Icon(
                                    Icons.Filled.Edit,
                                    contentDescription = stringResource(R.string.create_a_config),
                                )
                            }
                            SharedConfigImportMenu(
                                onImportFromClipboard = configComponent::onImportFromClipboard,
                                onManageSubscriptions = { onNavigate(Subscription) },
                                onScanQr = {
                                    onNavigate(
                                        ScanQR { result ->
                                            if (result.isEmpty()) {
                                                Toast
                                                    .makeText(context, "Cancelled", Toast.LENGTH_LONG)
                                                    .show()
                                            } else {
                                                configComponent.onImportFromLink(result)
                                            }
                                        },
                                    )
                                },
                                importFromClipboardLabel = stringResource(R.string.clipboard_import),
                                manageSubscriptionsLabel = stringResource(R.string.menu_subscription),
                                scanQrLabel = stringResource(R.string.qrcode_import),
                                additionalMenuItems = { dismiss ->
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.locate_selected_node)) },
                                        onClick = {
                                            dismiss()
                                            scope.launch { scrollToSelected() }
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Outlined.Star, contentDescription = null)
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.menu_delete_all)) },
                                        onClick = {
                                            dismiss()
                                            xrayViewmodel.showDeleteDialog()
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Outlined.DeleteForever, contentDescription = null)
                                        },
                                    )
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.bug_report_header)) },
                                        onClick = {
                                            dismiss()
                                            xrayViewmodel.bugReport(context)
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Outlined.BugReport, contentDescription = null)
                                        },
                                    )
                                },
                            )
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent, // Transparent to show Surface color
                            scrolledContainerColor = Color.Transparent
                        ),
                        scrollBehavior = scrollBehavior,
                    )
                }
            }
            SharedConfigSection(
                component = configComponent,
                modifier = Modifier.weight(1f),
                labels = configLabels,
                listState = listState,
                nodeDelayMap = nodeDelayMap,
                listModifier = Modifier.columnVerticalScrollbar(listState, 4.dp),
                nestedScrollConnection = scrollBehavior.nestedScrollConnection,
                onEmptyAddClick = { onNavigate(Edit) },
                onNodeSelected = { node ->
                    configComponent.onSelectNode(node.id)
                    onNavigate(Home)
                },
                onShareNode = { node -> xrayViewmodel.generateQRCode(node.id) },
                onEditNode = { node ->
                    onNavigate(
                        Detail(
                            id = node.id,
                            remark = node.remark,
                            protocol = node.protocolPrefix,
                            content = node.url,
                        ),
                    )
                },
                onDeleteNode = { node -> xrayViewmodel.showDeleteDialog(node.id) },
                filterTrailingContent = {
                    IconButton(
                        onClick = { xrayViewmodel.measureAllNodesDelay(context) },
                        modifier = Modifier.padding(end = 12.dp).size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Speed,
                            contentDescription = stringResource(R.string.config_speed_test_all_cd),
                            tint =
                                if (isTestingAll) {
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
                            modifier = Modifier.size(250.dp)
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

        if (bugReportDialog) {
            BugReportDialog(
                onDismiss = { xrayViewmodel.hideBugReportDialog() },
                onSubmit = { data ->
                    xrayViewmodel.submitBugReport(context, data)
                }
            )
        }

        val searchBarState = rememberSearchBarState()
        val textFieldState = rememberTextFieldState()
        // Add FocusManager and KeyboardController
        val focusManager = LocalFocusManager.current
        val keyboardController = LocalSoftwareKeyboardController.current
        var isInputEnabled by remember { mutableStateOf(true) }
        val expended = searchBarState.targetValue == SearchBarValue.Expanded
        val inputField =
            @Composable {
                SearchBarDefaults.InputField(
                    textFieldState = textFieldState,
                    searchBarState = searchBarState,
                    enabled = isInputEnabled,
                    onSearch = {
                        isInputEnabled = false
                        focusManager.clearFocus(force = true)
                        keyboardController?.hide()
                        scope.launch {
                            // #269
                            delay(400)
                            searchBarState.animateToCollapsed()
                            xrayViewmodel.onSearch(it)
                            isInputEnabled = true
                        }

                    },
                    placeholder = {
                        if (expended) {
                            Text(modifier = Modifier.clearAndSetSemantics {}, text = "Search")
                        }

                    },
                    leadingIcon = { Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = "search_lab",
                        tint = if (expended) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.surface
                    ) },
                )
            }
        AnimatedVisibility(
            visible = !listState.isAtBottom { isAtBottom ->
                if (isAtBottom) xrayViewmodel.hideNavigationBar() else xrayViewmodel.showNavigationBar()
            } && configState.nodes.isNotEmpty(),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align (BiasAlignment(0.8f,0.9f))
                .padding(bottom = bottomPadding)
        ) {
            SearchBar(
                state = searchBarState,
                inputField = inputField,
                colors = SearchBarDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                shadowElevation = 2.dp,
                modifier = Modifier.size(56.dp),
                shape = CircleShape
            )
        }
        @OptIn(FlowPreview::class)
        LaunchedEffect(textFieldState) {
            // Convert the text state into a Flow
            snapshotFlow { textFieldState.text.toString() }
                .debounce(300L) // Wait for 300ms pause in typing before emitting
                .distinctUntilChanged() // Ignore if the text hasn't actually changed
                .collectLatest { query ->
                    xrayViewmodel.onSearch(query)
                }
        }

        ExpandedFullScreenSearchBar(
            state = searchBarState,
            inputField = inputField,
            colors = SearchBarDefaults.colors(
                containerColor = MaterialTheme.colorScheme.background
            )
        ) {
            LazyColumn {
                items(queryNodes, key = { it.id }) { node ->
                    Column {
                        NodeCard(
                            node = node,
                            onChoose = {
                                scope.launch {
                                    textFieldState.clearText()
                                    searchBarState.animateToCollapsed()
                                    scrollToItemById(node.id)
                                }

                            },
                            selected =node.selected,
                            favorite = node.favorite,
                            onFavorite = {
                                xrayViewmodel.updateFavoriteById(node.id, !node.favorite)
                            },
                            roundCorner = false,
                            countryEmoji = node.countryISO
                        )
                        if (node != queryNodes.last()) {
                            HorizontalDivider(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 86.dp, end = 16.dp),
                                thickness = 0.6.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
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
/**
 * A highly optimized, flicker-free vertical scrollbar modifier for LazyColumn.
 */
fun Modifier.columnVerticalScrollbar(
    state: LazyListState,
    width: Dp = 4.dp,
    color: Color = Color.Gray,
    rightPadding: Dp = 2.dp,
    minThumbHeight: Dp = 20.dp // Prevent the scrollbar from disappearing on huge lists
): Modifier = composed {
    // 1. Use Animatable for smooth alpha transitions without triggering layout recompositions
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(state.isScrollInProgress) {
        if (state.isScrollInProgress) {
            alpha.animateTo(1f, tween(durationMillis = 150))
        } else {
            alpha.animateTo(0f, tween(durationMillis = 500))
        }
    }

    drawWithContent {
        drawContent()

        val currentAlpha = alpha.value
        // Return early if fully transparent
        if (currentAlpha == 0f) return@drawWithContent

        val layoutInfo = state.layoutInfo
        val visibleItemsInfo = layoutInfo.visibleItemsInfo
        val totalItemsCount = layoutInfo.totalItemsCount

        if (totalItemsCount == 0 || visibleItemsInfo.isEmpty()) return@drawWithContent

        // 2. Core Fix: Calculate average item size to prevent jitter when visible items count changes
        val averageItemSize = visibleItemsInfo.sumOf { it.size }.toFloat() / visibleItemsInfo.size
        val viewportHeight = size.height
        // Estimate the total height of all items combined
        val estimatedTotalSize = averageItemSize * totalItemsCount

        // No need for a scrollbar if all content fits the screen
        if (estimatedTotalSize <= viewportHeight) return@drawWithContent

        // 3. Calculate Thumb Height
        val heightProportion = (viewportHeight / estimatedTotalSize).coerceIn(0f, 1f)
        val minHeightPx = minThumbHeight.toPx()
        // Ensure the scrollbar doesn't become too small to see
        val thumbHeight = (viewportHeight * heightProportion).coerceAtLeast(minHeightPx)

        // 4. Calculate Scroll Progress (Fraction between 0.0 and 1.0)
        val firstItem = visibleItemsInfo.first()
        // offset is usually negative when scrolled down, so we invert it
        val firstItemOffset = -firstItem.offset.toFloat()
        val estimatedScrollOffset = (firstItem.index * averageItemSize) + firstItemOffset
        val maxEstimatedScrollOffset = (estimatedTotalSize - viewportHeight).coerceAtLeast(1f)

        val scrollProgress = (estimatedScrollOffset / maxEstimatedScrollOffset).coerceIn(0f, 1f)

        // 5. Calculate final Y coordinate
        val scrollbarOffsetY = scrollProgress * (viewportHeight - thumbHeight)

        // 6. Draw the rounded scrollbar thumb
        drawRoundRect(
            color = color,
            topLeft = Offset(
                x = size.width - width.toPx() - rightPadding.toPx(),
                y = scrollbarOffsetY
            ),
            size = Size(width.toPx(), thumbHeight),
            alpha = currentAlpha,
            cornerRadius = CornerRadius(width.toPx() / 2, width.toPx() / 2)
        )
    }
}
