package com.android.xrayfa.shared.ui.chrome

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val CollapsingTitleHeight = 64.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedListScaffold(
    title: String,
    modifier: Modifier = Modifier,
    largeTitle: Boolean = true,
    expandedTitleExtraStartPadding: Dp = 0.dp,
    collapseTitleOnScroll: Boolean = false,
    titleExpandKey: Any? = null,
    /**
     * 供需要让折叠状态跨导航存活的调用方传入。一个调用点要么始终传、要么始终不传 ——
     * 在两者之间切换会违反 `remember` 的规则，把折叠状态劈成两份。
     */
    titleCollapseState: TitleCollapseState? = null,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    footerUnderBar: @Composable () -> Unit = {},
    chromeColor: Color = Color.Unspecified,
    chromeShadowElevation: Dp = 0.dp,
    /** Maximum shadow when list content scrolls underneath the app bar. */
    scrolledChromeShadowElevation: Dp = 0.dp,
    chromeTonalElevation: Dp = 0.dp,
    showChromeHairline: Boolean = false,
    contentWindowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (innerBottomPadding: androidx.compose.foundation.layout.PaddingValues) -> Unit,
) {
    val titleCollapse = titleCollapseState ?: rememberTitleCollapseState()
    val density = LocalDensity.current
    LaunchedEffect(density) {
        titleCollapse.maxHeightPx = with(density) { CollapsingTitleHeight.toPx() }
    }
    LaunchedEffect(titleExpandKey) {
        if (titleExpandKey != null) {
            titleCollapse.expand()
        }
    }
    val scrollBehavior =
        if (largeTitle && !collapseTitleOnScroll) {
            TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
        } else {
            null
        }
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
                expandedTitleExtraStartPadding = expandedTitleExtraStartPadding,
                collapseTitleOnScroll = collapseTitleOnScroll,
                collapsedFraction = titleCollapse.collapsedFraction,
                navigationIcon = navigationIcon,
                actions = actions,
                footerUnderBar = footerUnderBar,
                scrollBehavior = scrollBehavior,
                barContainerColor = barContainerColor,
                pinnedChrome = pinnedChrome,
                chromeColor = chromeColor,
                chromeShadowElevation = chromeShadowElevation,
                scrolledChromeShadowElevation = scrolledChromeShadowElevation,
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
    expandedTitleExtraStartPadding: Dp,
    collapseTitleOnScroll: Boolean,
    collapsedFraction: Float,
    navigationIcon: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    footerUnderBar: @Composable () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior?,
    barContainerColor: Color,
    pinnedChrome: Boolean,
    chromeColor: Color,
    chromeShadowElevation: Dp,
    scrolledChromeShadowElevation: Dp,
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
                LargeTopAppBar(
                    title = {
                        // Material renders this slot twice with different text styles.
                        // Add the inset only to the expanded title; keep the compact row
                        // aligned with its navigation icon.
                        val isExpandedTitle =
                            LocalTextStyle.current.fontSize > MaterialTheme.typography.titleLarge.fontSize
                        Text(
                            title,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(
                                start = if (isExpandedTitle) expandedTitleExtraStartPadding else 0.dp,
                            ),
                        )
                    },
                    navigationIcon = navigationIcon,
                    actions = actions,
                    scrollBehavior = scrollBehavior,
                    colors =
                        TopAppBarDefaults.largeTopAppBarColors(
                            containerColor = barContainerColor,
                            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
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
            if (showChromeHairline) {
                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
        }
    }
    if (pinnedChrome || scrolledChromeShadowElevation > 0.dp) {
        val overlap = (scrollBehavior?.state?.overlappedFraction ?: 0f).coerceIn(0f, 1f)
        Surface(
            color = if (pinnedChrome) chromeColor else MaterialTheme.colorScheme.background,
            shadowElevation = chromeShadowElevation + scrolledChromeShadowElevation * overlap,
            tonalElevation = chromeTonalElevation,
        ) {
            barContent()
        }
    } else {
        barContent()
    }
}
