package com.android.xrayfa.shared.ui.widgets

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.android.xrayfa.shared.ui.nav.FloatingNavSearchChrome

@Composable
internal actual fun PlatformGlassFloatingActionButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier,
    nativeSystemImage: String?,
) {
    FloatingNavSearchChrome(onClick = onClick, modifier = modifier) {
        Icon(icon, contentDescription, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSurface)
    }
}
