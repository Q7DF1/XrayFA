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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.material3.Text

private val CollapsingTitleHeight = 64.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedListScaffold(
    title: String,
    modifier: Modifier = Modifier,
    largeTitle: Boolean = true,
    collapseTitleOnScroll: Boolean = false,
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
    collapsedFraction: Float,
    navigationIcon: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    footerUnderBar: @Composable () -> Unit,
    scrollBehavior: androidx.compose.material3.TopAppBarScrollBehavior?,
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
                LargeTopAppBar(
                    title = { Text(title, fontWeight = FontWeight.Bold) },
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
