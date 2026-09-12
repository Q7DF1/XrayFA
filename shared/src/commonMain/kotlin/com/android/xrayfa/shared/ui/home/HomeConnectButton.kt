package com.android.xrayfa.shared.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.xrayfa.shared.ui.theme.XrayBrand

@Composable
fun HomeConnectButton(
    isConnected: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    diameter: Dp = 200.dp,
    innerDiameter: Dp = 148.dp,
) {
    val buttonBrush =
        if (isConnected) {
            Brush.linearGradient(colors = listOf(XrayBrand.BlueLight, XrayBrand.BlueDeep))
        } else {
            Brush.linearGradient(
                colors =
                    listOf(
                        XrayBrand.Blue.copy(alpha = 0.22f),
                        XrayBrand.BlueDeep.copy(alpha = 0.12f),
                    ),
            )
        }
    val shadowColor = if (isConnected) XrayBrand.Blue.copy(alpha = 0.45f) else Color.Transparent
    val scale = remember { Animatable(1.0f) }
    val interactionSource = remember { MutableInteractionSource() }

    LaunchedEffect(isConnected) {
        scale.animateTo(
            targetValue = 1.2f,
            animationSpec = tween(durationMillis = 150),
        )
        scale.animateTo(
            targetValue = 1.0f,
            animationSpec =
                spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow,
                ),
        )
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(diameter),
    ) {
        if (isConnected) {
            HomeConnectPulseRings(color = XrayBrand.Blue)
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(innerDiameter)
                    .scale(scale.value)
                    .connectButtonSurface(
                        brush = buttonBrush,
                        shadowElevation = if (isConnected) 24.dp else 4.dp,
                        shadowColor = shadowColor,
                    )
                    .clickable(
                        enabled = enabled,
                        indication = null,
                        interactionSource = interactionSource,
                        onClick = onToggle,
                    ),
        ) {
            GuardCatMark(
                size = innerDiameter * 0.42f,
                bodyTint = if (isConnected) Color.White else XrayBrand.Blue,
                eyeTint = if (isConnected) XrayBrand.CatEyeGreen else XrayBrand.CatEyeAmber,
                contentDescription = "Toggle VPN",
            )
        }
    }
}

@Composable
private fun BoxScope.HomeConnectPulseRings(color: Color) {
    val transition = rememberInfiniteTransition(label = "pulse")
    repeat(2) { index ->
        val progress by
            transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(durationMillis = 2400, easing = LinearEasing),
                        initialStartOffset = StartOffset(index * 1200),
                    ),
                label = "ring$index",
            )
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .scale(0.74f + progress * 0.26f)
                    .clip(CircleShape)
                    .border(
                        width = 2.dp,
                        color = color.copy(alpha = (1f - progress) * 0.5f),
                        shape = CircleShape,
                    ),
        )
    }
}

internal expect fun Modifier.connectButtonSurface(
    brush: Brush,
    shadowElevation: Dp,
    shadowColor: Color,
): Modifier
