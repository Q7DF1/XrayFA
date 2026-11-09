package com.android.xrayfa.shared.ui.widgets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.android.xrayfa.shared.ui.nav.FloatingNavSearchChrome
import com.kyant.backdrop.isRuntimeShaderSupported

@Composable
internal actual fun PlatformGlassFloatingActionButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier,
    nativeSystemImage: String?,
) {
    if (!isRuntimeShaderSupported()) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shadowElevation = 3.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription, Modifier.size(26.dp))
            }
        }
        return
    }
    FloatingNavSearchChrome(onClick = onClick, modifier = modifier) {
        Icon(icon, contentDescription, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSurface)
    }
}
