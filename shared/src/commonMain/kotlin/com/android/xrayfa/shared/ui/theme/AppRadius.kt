package com.android.xrayfa.shared.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Shared corner-radius tokens. Prefer these over one-off dp values. */
object AppRadius {
    val medium = 16.dp
    val large = 24.dp
    /** Search fields and other compact pills. */
    val field = 28.dp
    val shapeMedium = RoundedCornerShape(medium)
    // TODO: Migrate remaining hardcoded 24.dp corner shapes to shapeLarge.
    val shapeLarge = RoundedCornerShape(large)
    val shapeField = RoundedCornerShape(field)
}
