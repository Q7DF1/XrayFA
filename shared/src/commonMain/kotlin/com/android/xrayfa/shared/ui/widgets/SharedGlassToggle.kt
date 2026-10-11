package com.android.xrayfa.shared.ui.widgets

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Controlled platform switch. The caller owns persistence and supplies an accessible label. */
@Composable
fun SharedGlassToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    PlatformGlassToggle(checked, onCheckedChange, contentDescription, modifier, enabled)
}

@Composable
internal expect fun PlatformGlassToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    contentDescription: String,
    modifier: Modifier,
    enabled: Boolean,
)
