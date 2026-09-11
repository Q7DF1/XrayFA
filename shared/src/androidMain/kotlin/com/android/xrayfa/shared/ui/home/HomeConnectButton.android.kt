package com.android.xrayfa.shared.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

internal actual fun Modifier.connectButtonSurface(
    brush: Brush,
    shadowElevation: Dp,
    shadowColor: Color,
): Modifier =
    shadow(
        elevation = shadowElevation,
        shape = CircleShape,
        spotColor = shadowColor,
        ambientColor = shadowColor,
    )
        .clip(CircleShape)
        .background(brush)
