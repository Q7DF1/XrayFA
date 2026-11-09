package com.android.xrayfa.shared.ui.nav

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
    // Older devices paint Material surfaces; avoid recording an unused backdrop each frame.
    if (!isRuntimeShaderSupported()) {
        CompositionLocalProvider(
            LocalFloatingNavBackdropLayer provides null,
            LocalFloatingNavBackdrop provides null,
            content = content,
        )
        return
    }
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
    nativeItems: List<FloatingNavItem>,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    val backdrop = liquidNavBackdropOrNull()
    if (backdrop == null) {
        val colors = MaterialTheme.colorScheme
        Surface(
            modifier = modifier.height(FloatingNavBarHeight).fillMaxWidth(),
            shape = CircleShape,
            color = colors.surfaceContainerLowest,
            border = BorderStroke(0.5.dp, colors.outlineVariant.copy(alpha = 0.35f)),
            shadowElevation = 3.dp,
        ) {
            BoxWithConstraints(Modifier.padding(6.dp)) {
                val itemWidth = maxWidth / tabsCount.coerceAtLeast(1)
                val selection by animateFloatAsState(
                    targetValue = selectedIndex.toFloat(),
                    animationSpec = spring(dampingRatio = 1f, stiffness = 450f),
                    label = "navigationSelection",
                )
                Box(
                    Modifier.offset(x = itemWidth * selection)
                        .width(itemWidth).fillMaxHeight()
                        .background(colors.onSurface.copy(alpha = 0.07f), CircleShape),
                )
                Row(Modifier.fillMaxWidth().selectableGroup()) {
                    nativeItems.forEachIndexed { index, item ->
                        val selected = index == selectedIndex
                        val contentColor by animateColorAsState(
                            if (selected) colors.onSurface else colors.onSurfaceVariant,
                            label = "navigationContent",
                        )
                        Column(
                            modifier = Modifier.weight(1f).fillMaxHeight()
                                .clip(CircleShape)
                                .selectable(
                                    selected = selected,
                                    role = Role.Tab,
                                    onClick = { onIndexSettled(index) },
                                ),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                        ) {
                            Icon(item.icon, null, Modifier.size(22.dp), tint = contentColor)
                            Text(
                                text = item.label,
                                color = contentColor,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
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
    nativeSystemImage: String?,
    nativeContentDescription: String,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    val backdrop = liquidNavBackdropOrNull()
    if (backdrop == null) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
            shadowElevation = 3.dp,
        ) {
            Box(contentAlignment = Alignment.Center) { content() }
        }
        return
    }
    val glassModifier = run {
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

internal actual fun floatingNavNeedsBottomFade(): Boolean = true
