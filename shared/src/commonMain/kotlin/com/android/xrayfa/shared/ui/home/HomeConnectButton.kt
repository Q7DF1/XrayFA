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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Large circular VPN toggle — visual parity with Android [V2rayStarterLarge].
 * Platform-specific permission / service logic stays in the caller via [onToggle].
 */
@Composable
fun HomeConnectButton(
    isConnected: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant

    val buttonBrush =
        if (isConnected) {
            Brush.linearGradient(colors = listOf(primary, tertiary))
        } else {
            Brush.linearGradient(
                colors =
                    listOf(
                        surfaceVariant,
                        surfaceVariant.copy(alpha = 0.65f),
                    ),
            )
        }

    val shadowColor = if (isConnected) primary.copy(alpha = 0.45f) else Color.Transparent
    val scale = remember { Animatable(1.0f) }

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
        modifier = modifier.size(200.dp),
    ) {
        if (isConnected) {
            HomeConnectPulseRings(color = primary)
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(148.dp)
                    .scale(scale.value)
                    .shadow(
                        elevation = if (isConnected) 24.dp else 4.dp,
                        shape = CircleShape,
                        spotColor = shadowColor,
                        ambientColor = shadowColor,
                    )
                    .clip(CircleShape)
                    .background(buttonBrush),
        ) {
            IconButton(
                onClick = {
                    if (enabled) onToggle()
                },
                enabled = enabled,
                modifier = Modifier.fillMaxSize(),
            ) {
                Icon(
                    imageVector =
                        if (isConnected) {
                            Icons.Default.Check
                        } else {
                            Icons.Default.PowerSettingsNew
                        },
                    contentDescription = "Toggle VPN",
                    tint =
                        if (isConnected) {
                            Color.White
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    modifier = Modifier.size(60.dp),
                )
            }
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
