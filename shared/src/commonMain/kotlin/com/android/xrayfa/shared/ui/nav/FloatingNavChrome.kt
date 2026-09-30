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

@Composable
expect fun FloatingNavSearchChrome(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
)
