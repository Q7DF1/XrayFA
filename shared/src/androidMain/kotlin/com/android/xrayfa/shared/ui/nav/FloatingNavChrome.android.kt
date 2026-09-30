package com.android.xrayfa.shared.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.isRuntimeShaderSupported
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.Capsule

/** Backdrop sampled from `ChildPages`. Android only; `null` falls back to a solid surface. */
val LocalFloatingNavBackdrop = staticCompositionLocalOf<Backdrop?> { null }

private val LocalFloatingNavBackdropLayer = staticCompositionLocalOf<LayerBackdrop?> { null }

/**
 * Returns the backdrop only when its effects can run (lens needs RuntimeShader, API 33+, per
 * Backdrop's own [isRuntimeShaderSupported]); otherwise null so callers paint the solid surface.
 */
@Composable
private fun liquidNavBackdropOrNull(): Backdrop? {
    val backdrop = LocalFloatingNavBackdrop.current
    return if (backdrop != null && isRuntimeShaderSupported()) backdrop else null
}

@Composable
actual fun ProvideFloatingNavBackdrop(content: @Composable () -> Unit) {
    val layerBackdrop = rememberLayerBackdrop()
    CompositionLocalProvider(
        LocalFloatingNavBackdropLayer provides layerBackdrop,
        LocalFloatingNavBackdrop provides layerBackdrop,
    ) {
        content()
    }
}

@Composable
actual fun FloatingNavBackdropSource(
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    val layer = LocalFloatingNavBackdropLayer.current
    Box(if (layer != null) modifier.layerBackdrop(layer) else modifier) {
        content()
    }
}

@Composable
actual fun FloatingNavChrome(
    selectedIndex: Int,
    onIndexSettled: (Int) -> Unit,
    tabsCount: Int,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    val backdrop = liquidNavBackdropOrNull()
    if (backdrop == null) {
        val shape = RoundedCornerShape(32.dp)
        Box(
            modifier
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest, shape)
                .height(FloatingNavBarHeight)
                .fillMaxWidth(),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(FloatingNavBarHeight)
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                content()
            }
            Row(Modifier.fillMaxWidth().height(FloatingNavBarHeight)) {
                repeat(tabsCount) { index ->
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = null,
                                indication = null,
                                role = Role.Tab,
                            ) { onIndexSettled(index) },
                    )
                }
            }
        }
    } else {
        LiquidNavBar(
            selectedIndex = selectedIndex,
            onIndexSettled = onIndexSettled,
            tabsCount = tabsCount,
            backdrop = backdrop,
            modifier = modifier,
        ) {
            content()
        }
    }
}

@Composable
actual fun FloatingNavSearchChrome(
    onClick: () -> Unit,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    val backdrop = liquidNavBackdropOrNull()
    val glassModifier = if (backdrop == null) {
        modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape)
    } else {
        val containerColor = liquidNavContainerColor(isLiquidNavLightTheme())
        modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { Capsule() },
            effects = {
                vibrancy()
                blur(8f.dp.toPx())
                lens(24f.dp.toPx(), 24f.dp.toPx())
            },
            onDrawSurface = { drawRect(containerColor) },
        )
    }
    Box(
        glassModifier
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
