package com.android.xrayfa.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils

/**
 * Keep a hint of the wallpaper in large surfaces without making them compete with
 * controls. Reduce CIELAB chroma while retaining lightness and hue, rather than
 * mixing with white (which would flatten dark themes and surface hierarchy).
 *
 * Accent, state and error colors remain the system's original accessible pairs.
 * Neutral text and outlines use the same treatment as their surfaces.
 */
internal fun ColorScheme.withSubtleDynamicSurfaces(): ColorScheme {
    fun Color.subtle(): Color {
        val lab = DoubleArray(3)
        ColorUtils.colorToLAB(toArgb(), lab)
        return Color(ColorUtils.LABToColor(lab[0], lab[1] * 0.35, lab[2] * 0.35))
            .copy(alpha = alpha)
    }

    return copy(
        background = background.subtle(),
        onBackground = onBackground.subtle(),
        surface = surface.subtle(),
        onSurface = onSurface.subtle(),
        surfaceVariant = surfaceVariant.subtle(),
        onSurfaceVariant = onSurfaceVariant.subtle(),
        surfaceDim = surfaceDim.subtle(),
        surfaceBright = surfaceBright.subtle(),
        surfaceContainerLowest = surfaceContainerLowest.subtle(),
        surfaceContainerLow = surfaceContainerLow.subtle(),
        surfaceContainer = surfaceContainer.subtle(),
        surfaceContainerHigh = surfaceContainerHigh.subtle(),
        surfaceContainerHighest = surfaceContainerHighest.subtle(),
        inverseSurface = inverseSurface.subtle(),
        inverseOnSurface = inverseOnSurface.subtle(),
        outline = outline.subtle(),
        outlineVariant = outlineVariant.subtle(),
    )
}
