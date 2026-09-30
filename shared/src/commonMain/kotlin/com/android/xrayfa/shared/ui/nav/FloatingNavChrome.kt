package com.android.xrayfa.shared.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun FloatingNavChrome(
    selectedIndex: Int,
    onIndexSettled: (Int) -> Unit,
    tabsCount: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
)

/**
 * Installs the backdrop that [FloatingNavChrome] and [FloatingNavSearchChrome] sample.
 *
 * Wrap both the tab pages (via [FloatingNavBackdropSource]) and the nav in this, so the nav
 * can read the backdrop while the pages record into it. Android only; iOS is a no-op.
 */
@Composable
expect fun ProvideFloatingNavBackdrop(
    content: @Composable () -> Unit,
)

/** Marks [content] as the pixels the floating nav samples. Must be under [ProvideFloatingNavBackdrop]. */
@Composable
expect fun FloatingNavBackdropSource(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
)

@Composable
expect fun FloatingNavSearchChrome(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
)
