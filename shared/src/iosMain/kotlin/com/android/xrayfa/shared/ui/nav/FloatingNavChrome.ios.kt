package com.android.xrayfa.shared.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

// Stub: Task 5 replaces this with UIGlassEffect / UIBlurEffect chrome.
@Composable
actual fun FloatingNavChrome(
    selectedIndex: Int,
    onIndexSettled: (Int) -> Unit,
    tabsCount: Int,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(32.dp)
    Box(
        modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, shape),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            content()
        }
        if (tabsCount == 2) {
            Row(Modifier.matchParentSize()) {
                repeat(tabsCount) { index ->
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { onIndexSettled(index) },
                    )
                }
            }
        }
    }
}

@Composable
actual fun ProvideFloatingNavBackdrop(content: @Composable () -> Unit) {
    content()
}

@Composable
actual fun FloatingNavBackdropSource(
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier) { content() }
}

@Composable
actual fun FloatingNavSearchChrome(
    onClick: () -> Unit,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
