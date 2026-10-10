package com.android.xrayfa.shared.ui.widgets

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.isRuntimeShaderSupported
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.delay

/** Uses Backdrop's LiquidToggle optical treatment, with controlled state and Compose gestures. */
@Composable
internal actual fun PlatformGlassToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    contentDescription: String,
    modifier: Modifier,
    enabled: Boolean,
) {
    val accessibleModifier = modifier.semantics { this.contentDescription = contentDescription }
    if (!isRuntimeShaderSupported()) {
        Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = accessibleModifier, enabled = enabled)
        return
    }
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val travel = with(LocalDensity.current) { 20.dp.toPx() }
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var previousChecked by remember { mutableStateOf(checked) }
    var settling by remember { mutableStateOf(false) }
    LaunchedEffect(checked) {
        if (previousChecked != checked) {
            previousChecked = checked
            settling = true
            delay(180)
            settling = false
        }
    }
    val animatedFraction by animateFloatAsState(
        if (checked) 1f else 0f,
        spring(dampingRatio = 0.8f, stiffness = 700f),
        label = "glassTogglePosition",
    )
    val progress by animateFloatAsState(
        if (enabled && (pressed || dragging || settling)) 1f else 0f,
        spring(dampingRatio = 0.7f, stiffness = 500f),
        label = "glassTogglePress",
    )
    val fraction = if (dragging) dragFraction else animatedFraction
    val trackBackdrop = rememberLayerBackdrop()
    val surfaceBackdrop = rememberLayerBackdrop()
    val colors = MaterialTheme.colorScheme
    val backdrop = rememberCombinedBackdrop(
        surfaceBackdrop,
        rememberBackdrop(trackBackdrop) { drawBackdrop ->
            // Compress the sampled track to expose its curved boundary through the lens.
            scale(2f / 3f + progress / 12f, 0.75f * progress) { drawBackdrop() }
        },
    )
    Box(
        accessibleModifier
            .size(68.dp, 48.dp)
            .alpha(if (enabled) 1f else 0.38f)
            .toggleable(
                value = checked,
                interactionSource = interactions,
                indication = null,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .draggable(
                state = rememberDraggableState { delta ->
                    dragFraction = (dragFraction + delta / travel).coerceIn(0f, 1f)
                },
                orientation = Orientation.Horizontal,
                enabled = enabled,
                reverseDirection = isRtl,
                onDragStarted = { dragFraction = animatedFraction; dragging = true },
                onDragStopped = {
                    val next = dragFraction >= 0.5f
                    dragging = false
                    if (next != checked) onCheckedChange(next)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(68.dp, 48.dp).layerBackdrop(surfaceBackdrop)
            .background(colors.surfaceContainerLowest))
        Box(
            Modifier.size(64.dp, 28.dp)
                .layerBackdrop(trackBackdrop)
                .background(lerp(colors.surfaceContainerHighest, colors.primary, fraction), CircleShape),
        )
        Box(
            Modifier.align(Alignment.CenterStart)
                .offset(x = (4 + 20 * fraction).dp)
                .size(40.dp, 24.dp)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { CircleShape },
                    effects = {
                        blur(8.dp.toPx() * (1f - progress))
                        lens(5.dp.toPx() * progress, 10.dp.toPx() * progress, chromaticAberration = true)
                    },
                    highlight = { Highlight.Ambient.copy(alpha = progress) },
                    shadow = { Shadow(radius = 4.dp, color = Color.Black.copy(alpha = 0.05f)) },
                    innerShadow = { InnerShadow(radius = 4.dp * progress, alpha = progress) },
                    layerBlock = {
                        scaleX = 1f + 0.35f * progress
                        scaleY = 1f + 0.5f * progress
                    },
                    onDrawSurface = { drawRect(Color.White.copy(alpha = 1f - progress)) },
                ),
        )
    }
}
