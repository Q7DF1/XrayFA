package com.android.xrayfa.shared.ui.widgets

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** Platform glass action with an optional SF Symbol for the native iOS control. */
@Composable
fun SharedGlassFloatingActionButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    nativeSystemImage: String? = null,
) {
    PlatformGlassFloatingActionButton(onClick, icon, contentDescription, modifier.size(56.dp), nativeSystemImage)
}

@Composable
internal expect fun PlatformGlassFloatingActionButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier,
    nativeSystemImage: String?,
)
