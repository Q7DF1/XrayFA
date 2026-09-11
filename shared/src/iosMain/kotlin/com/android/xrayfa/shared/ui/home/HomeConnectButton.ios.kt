package com.android.xrayfa.shared.ui.home

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal actual fun Modifier.connectButtonSurface(
    brush: Brush,
    shadowElevation: Dp,
    shadowColor: Color,
): Modifier =
    drawBehind {
        val radius = size.minDimension / 2f
        if (shadowElevation > 0.dp && shadowColor.alpha > 0f) {
            drawCircle(
                color = shadowColor,
                radius = radius,
                center = center + Offset(0f, 6.dp.toPx()),
            )
        }
        drawCircle(brush = brush, radius = radius)
    }
