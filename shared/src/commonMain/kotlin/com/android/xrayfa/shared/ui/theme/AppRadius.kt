package com.android.xrayfa.shared.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Corner radii shared by chrome. Prefer these over one-off dp values. */
object AppRadius {
    val small = 12.dp
    val medium = 16.dp
    /** Search fields and other compact pills. */
    val field = 28.dp
    val shapeSmall = RoundedCornerShape(small)
    val shapeMedium = RoundedCornerShape(medium)
    val shapeField = RoundedCornerShape(field)
}
