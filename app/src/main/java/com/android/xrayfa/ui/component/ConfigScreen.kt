package com.android.xrayfa.ui.component

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.material.icons.outlined.Mic
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
import com.android.xrayfa.ui.QRCodeActivity
import com.android.xrayfa.ui.ScanQRResultContract
import com.android.xrayfa.ui.navigation.Config
import com.android.xrayfa.ui.navigation.Detail
import com.android.xrayfa.ui.navigation.Edit
import com.android.xrayfa.ui.navigation.Home
import com.android.xrayfa.ui.navigation.NavigateDestination
import com.android.xrayfa.ui.navigation.Subscription
import com.android.xrayfa.viewmodel.XrayViewmodel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

import androidx.compose.animation.AnimatedVisibilityScope

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ConfigScreen(
    xrayViewmodel: XrayViewmodel,
    bottomPadding: Dp = 0.dp,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onNavigate: (NavigateDestination) -> Unit
) {
    val nodes by xrayViewmodel.nodes.collectAsState()
    val queryNodes by xrayViewmodel.queryNodes.collectAsState()
    val qrBitMap by xrayViewmodel.qrBitmap.collectAsState()
    val deleteDialog by xrayViewmodel.deleteDialog.collectAsState()
    val subscriptions by xrayViewmodel.subscriptions.collectAsState()
    val selectedSubId by xrayViewmodel.selectedSubscriptionId.collectAsState()

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
    
    // Build filter list
    val filters = remember(subscriptions) {
        val list = mutableListOf<Pair<Int, String>>()
        list.add(XrayViewmodel.SUB_ALL to "All")
        list.add(XrayViewmodel.SUB_MANUAL to "Manual")
        subscriptions.forEach { 
            list.add(it.id to it.mark)
        }
        list
    }

    val barcodeLauncher = rememberLauncherForActivityResult(ScanQRResultContract()) {
            result->
        if (result.isEmpty()) {
            Toast.makeText(context, "Cancelled", Toast.LENGTH_LONG).show();
        }else {
            xrayViewmodel.addLink(result)
        }
    }

    // Function to locate and scroll to a specific item by ID
    suspend fun scrollToItemById(id: Int) {
        val index = nodes.indexOfFirst { it.id == id }
        if (index != -1) {
            // Animate scroll to the found index
            listState.animateScrollToItem(index)
        }
    }

    // Function to locate and scroll to the selected item
    suspend fun scrollToSelected() {
        val index = nodes.indexOfFirst { it.selected }
        if (index != -1) {
            // Scroll to the selected item
            listState.animateScrollToItem(index)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize()){
            Surface(
                shadowElevation = appBarElevation,
                color = MaterialTheme.colorScheme.surface, // Use surface instead of background
                modifier = Modifier.zIndex(1f)
            ) {
                Column {
                    TopAppBar(
                        title = {
                            Text(stringResource(Config.title), fontWeight = FontWeight.Bold)
                        },
                        actions = {
                            var checked by remember { mutableStateOf(false) }

                            Box(
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                SplitButtonLayout(
                                    leadingButton = {
                                        SplitButtonDefaults.LeadingButton(onClick = {
                                            onNavigate(Edit)
                                        }) {
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
                                        text = { Text(stringResource(R.string.clipboard_import)) },
                                        onClick = {
                                            xrayViewmodel.addXrayConfigFromClipboard(context)
                                            checked = false
                                        },
                                        leadingIcon = { Icon(Icons.Outlined.ContentCut, contentDescription = null) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.qrcode_import)) },
                                        onClick = {
                                            val intent = Intent(context, QRCodeActivity::class.java)
                                            barcodeLauncher.launch(intent)
                                            checked = false
                                        },
                                        leadingIcon = { Icon(Icons.Outlined.QrCodeScanner, contentDescription = null) },
                                    )
                                    DropdownMenuItem(
                                        text = {Text(stringResource(R.string.menu_subscription))},
                                        onClick = {
                                            checked = false
                                            onNavigate(Subscription)
                                            //xrayViewmodel.startSubscriptionActivity(context)
                                        },
                                        leadingIcon = {Icon(Icons.Outlined.Subscriptions, contentDescription = null)}
                                    )
                                    DropdownMenuItem(
                                        text = {Text(stringResource(R.string.locate_selected_node))},
                                        onClick = {
                                            checked = false
                                            scope.launch { scrollToSelected() }
                                        },
                                        leadingIcon = {Icon(Icons.Outlined.Star, contentDescription = null)}
                                    )
                                    DropdownMenuItem(
                                        text = {Text(stringResource(R.string.menu_delete_all))},
                                        onClick = {
                                            checked = false
                                            xrayViewmodel.showDeleteDialog(/*delete all*/)
                                        },
                                        leadingIcon = {Icon(Icons.Outlined.DeleteForever, contentDescription = null)}
                                    )
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = { Text("Bug Report") },
                                        onClick = {
                                            checked = false
                                            xrayViewmodel.bugReport(context)
                                        },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Outlined.BugReport,
                                                contentDescription = null
                                            )
                                        }
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent, // Transparent to show Surface color
                            scrolledContainerColor = Color.Transparent
                        ),
                        scrollBehavior = scrollBehavior,
                    )
                    
                    // Filter Chips Row
                    if (subscriptions.isNotEmpty()) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(filters.size) { index ->
                                val (id, label) = filters[index]
                                FilterChip(
                                    selected = selectedSubId == id,
                                    onClick = { xrayViewmodel.selectSubscription(id) },
                                    label = { Text(label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = selectedSubId == id,
                                        borderColor = MaterialTheme.colorScheme.outlineVariant,
                                        selectedBorderColor = Color.Transparent
                                    )
                                )
                            }
                        }
                    }
                }
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
                        .columnVerticalScrollbar(listState,4.dp)
                ) {
                    items(nodes, key = {it.id}) { node ->
                        Column(modifier = Modifier.animateItem()) {
                            with(sharedTransitionScope) {
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
                                    onEdit = {
                                        onNavigate(
                                            Detail (
                                                id = node.id,
                                                remark = node.remark,
                                                protocol = node.protocolPrefix,
                                                content = node.url
                                            )
                                        )
                                    },
                                    selected =node.selected,
                                    roundCorner = false,
                                    countryEmoji = node.countryISO,
                                    modifier = Modifier.sharedElement(
                                        sharedTransitionScope.rememberSharedContentState(key = node.id),
                                        animatedVisibilityScope = LocalNavAnimatedContentScope.current,
                                    )
                                )
                            }

                            if(node != nodes.last()) {
                                HorizontalDivider(
                                    modifier = Modifier.fillMaxSize()
                                        .padding(horizontal = 48.dp),
                                    thickness = 1.dp
                                )
                            }
                        }
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
                    trailingIcon = {
                        if (expended) {
                            Icon(
                                imageVector = Icons.Outlined.Mic,
                                contentDescription = "voice_search_lab"
                            )
                        }
                    },
                )
            }
        AnimatedVisibility(
            visible = !listState.isAtBottom { isAtBottom ->
                if (isAtBottom) xrayViewmodel.hideNavigationBar() else xrayViewmodel.showNavigationBar()
            },
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
                        roundCorner = false,
                        countryEmoji = node.countryISO
                    )
                        if(node != nodes.last()) {
                            HorizontalDivider(
                                modifier = Modifier.fillMaxSize()
                                    .padding(horizontal = 48.dp),
                                thickness = 1.dp
                            )
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