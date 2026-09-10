package com.android.xrayfa.shared.ui.chrome

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.TopAppBarState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp

private val CollapsingTitleHeight = 64.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedListScaffold(
    title: String,
    modifier: Modifier = Modifier,
    largeTitle: Boolean = true,
    collapseTitleOnScroll: Boolean = false,
    lockCollapsedTitle: Boolean = false,
    titleExpandKey: Any? = null,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    footerUnderBar: @Composable () -> Unit = {},
    chromeColor: Color = Color.Unspecified,
    chromeShadowElevation: Dp = 0.dp,
    chromeTonalElevation: Dp = 0.dp,
    showChromeHairline: Boolean = false,
    contentWindowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (innerBottomPadding: androidx.compose.foundation.layout.PaddingValues) -> Unit,
) {
    val titleCollapse = rememberTitleCollapseState()
    val density = LocalDensity.current
    LaunchedEffect(density) {
        titleCollapse.maxHeightPx = with(density) { CollapsingTitleHeight.toPx() }
    }
    LaunchedEffect(titleExpandKey) {
        if (titleExpandKey != null) {
            titleCollapse.expand()
        }
    }
    val collapsedTitleLock = remember { CollapsedTitleLock() }
    val rawScrollBehavior =
        if (largeTitle && !collapseTitleOnScroll) {
            TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
        } else {
            null
        }
    val scrollBehavior =
        if (rawScrollBehavior != null && lockCollapsedTitle) {
            remember(rawScrollBehavior) {
                LockingTopAppBarScrollBehavior(rawScrollBehavior, collapsedTitleLock)
            }
        } else {
            rawScrollBehavior
        }
    LaunchedEffect(scrollBehavior?.state?.collapsedFraction, lockCollapsedTitle) {
        val behavior = scrollBehavior ?: return@LaunchedEffect
        if (lockCollapsedTitle && behavior.state.collapsedFraction >= 0.99f) {
            collapsedTitleLock.lock()
            behavior.state.heightOffset = behavior.state.heightOffsetLimit
        }
    }
    val showCollapsedHairline =
        lockCollapsedTitle &&
            (
                collapsedTitleLock.locked ||
                    (scrollBehavior?.state?.collapsedFraction ?: 0f) > 0.02f
            )
    val nestedScrollConnection =
        when {
            collapseTitleOnScroll -> titleCollapse.connection
            scrollBehavior != null -> scrollBehavior.nestedScrollConnection
            else -> null
        }
    val pinnedChrome = chromeColor != Color.Unspecified
    val barContainerColor =
        if (pinnedChrome || collapseTitleOnScroll) {
            Color.Transparent
        } else {
            MaterialTheme.colorScheme.background
        }
    Scaffold(
        modifier =
            if (nestedScrollConnection != null) {
                modifier.nestedScroll(nestedScrollConnection)
            } else {
                modifier
            },
        contentWindowInsets = contentWindowInsets,
        floatingActionButton = floatingActionButton,
        topBar = {
            SharedListScaffoldTopBar(
                title = title,
                largeTitle = largeTitle && !collapseTitleOnScroll,
                collapseTitleOnScroll = collapseTitleOnScroll,
                lockCollapsedTitle = lockCollapsedTitle,
                collapsedFraction = titleCollapse.collapsedFraction,
                showCollapsedHairline = showCollapsedHairline,
                navigationIcon = navigationIcon,
                actions = actions,
                footerUnderBar = footerUnderBar,
                scrollBehavior = scrollBehavior,
                barContainerColor = barContainerColor,
                pinnedChrome = pinnedChrome,
                chromeColor = chromeColor,
                chromeShadowElevation = chromeShadowElevation,
                chromeTonalElevation = chromeTonalElevation,
                showChromeHairline = showChromeHairline,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Box(Modifier.fillMaxSize()) {
            content(innerPadding)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SharedListScaffoldTopBar(
    title: String,
    largeTitle: Boolean,
    collapseTitleOnScroll: Boolean,
    lockCollapsedTitle: Boolean,
    collapsedFraction: Float,
    showCollapsedHairline: Boolean,
    navigationIcon: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    footerUnderBar: @Composable () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior?,
    barContainerColor: Color,
    pinnedChrome: Boolean,
    chromeColor: Color,
    chromeShadowElevation: Dp,
    chromeTonalElevation: Dp,
    showChromeHairline: Boolean,
) {
    val barContent: @Composable () -> Unit = {
        Column(
            modifier =
                if (collapseTitleOnScroll) {
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars)
                } else {
                    Modifier
                },
        ) {
            if (collapseTitleOnScroll) {
                val titleHeight = CollapsingTitleHeight * (1f - collapsedFraction)
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(titleHeight)
                            .clipToBounds()
                            .graphicsLayer { alpha = 1f - collapsedFraction },
                ) {
                    TopAppBar(
                        title = { Text(title, fontWeight = FontWeight.Bold) },
                        navigationIcon = navigationIcon,
                        actions = actions,
                        windowInsets = WindowInsets(0, 0, 0, 0),
                        colors =
                            TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.background,
                                scrolledContainerColor = MaterialTheme.colorScheme.background,
                            ),
                    )
                }
            } else if (largeTitle) {
                val scrolledContainer =
                    if (lockCollapsedTitle) {
                        barContainerColor
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer
                    }
                LargeTopAppBar(
                    title = { Text(title, fontWeight = FontWeight.Bold) },
                    navigationIcon = navigationIcon,
                    actions = actions,
                    scrollBehavior = scrollBehavior,
                    colors =
                        TopAppBarDefaults.largeTopAppBarColors(
                            containerColor = barContainerColor,
                            scrolledContainerColor = scrolledContainer,
                        ),
                )
            } else {
                TopAppBar(
                    title = { Text(title, fontWeight = FontWeight.Bold) },
                    navigationIcon = navigationIcon,
                    actions = actions,
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = barContainerColor,
                            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        ),
                )
            }
            footerUnderBar()
            if (showChromeHairline || showCollapsedHairline) {
                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
        }
    }
    if (pinnedChrome) {
        Surface(
            color = chromeColor,
            shadowElevation = chromeShadowElevation,
            tonalElevation = chromeTonalElevation,
        ) {
            barContent()
        }
    } else {
        barContent()
    }
}

private class CollapsedTitleLock {
    var locked by mutableStateOf(false)
        private set

    fun lock() {
        locked = true
    }
}

@OptIn(ExperimentalMaterial3Api::class)
private class LockingTopAppBarScrollBehavior(
    private val base: TopAppBarScrollBehavior,
    private val lock: CollapsedTitleLock,
) : TopAppBarScrollBehavior {
    override val state: TopAppBarState
        get() = base.state
    override val isPinned: Boolean
        get() = base.isPinned
    override val snapAnimationSpec
        get() = if (lock.locked) null else base.snapAnimationSpec
    override val flingAnimationSpec
        get() = if (lock.locked) null else base.flingAnimationSpec
    override val nestedScrollConnection: NestedScrollConnection =
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (lock.locked) return Offset.Zero
                return base.nestedScrollConnection.onPreScroll(available, source).also { maybeLock() }
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (lock.locked) return Offset.Zero
                return base.nestedScrollConnection
                    .onPostScroll(consumed, available, source)
                    .also { maybeLock() }
            }

            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity,
            ): Velocity {
                if (lock.locked) return Velocity.Zero
                return base.nestedScrollConnection.onPostFling(consumed, available).also { maybeLock() }
            }

            private fun maybeLock() {
                if (base.state.collapsedFraction >= 0.99f) {
                    lock.lock()
                    base.state.heightOffset = base.state.heightOffsetLimit
                }
            }
        }
}
