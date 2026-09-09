package com.android.xrayfa.shared.ui.theme

import androidx.compose.ui.graphics.Color

internal val PrimaryLight = Color(0xFF0061A4)
internal val OnPrimaryLight = Color(0xFFFFFFFF)
internal val PrimaryContainerLight = Color(0xFFD1E4FF)
internal val OnPrimaryContainerLight = Color(0xFF001D36)

internal val PrimaryDark = Color(0xFF9ECAFF)
internal val OnPrimaryDark = Color(0xFF003258)
internal val PrimaryContainerDark = Color(0xFF00497D)
internal val OnPrimaryContainerDark = Color(0xFFD1E4FF)

internal val SecondaryLight = Color(0xFF535F70)
internal val OnSecondaryLight = Color(0xFFFFFFFF)
internal val SecondaryContainerLight = Color(0xFFD7E3F7)
internal val OnSecondaryContainerLight = Color(0xFF101C2B)

internal val SecondaryDark = Color(0xFFBBC7DB)
internal val OnSecondaryDark = Color(0xFF253140)
internal val SecondaryContainerDark = Color(0xFF3B4858)
internal val OnSecondaryContainerDark = Color(0xFFD7E3F7)

internal val TertiaryLight = Color(0xFF6B5778)
internal val OnTertiaryLight = Color(0xFFFFFFFF)
internal val TertiaryContainerLight = Color(0xFFF2DAFF)
internal val OnTertiaryContainerLight = Color(0xFF251431)

internal val TertiaryDark = Color(0xFFD7BEE4)
internal val OnTertiaryDark = Color(0xFF3B2948)
internal val TertiaryContainerDark = Color(0xFF523F5F)
internal val OnTertiaryContainerDark = Color(0xFFF2DAFF)

// Neutral surface ladder: same hue/chroma as Surface*, tones from Material 3
// (light N-100/98/96/94/92/90/87, dark N-4/6/10/12/17/22/24).
internal val SurfaceLight = Color(0xFFF8F9FF)
internal val OnSurfaceLight = Color(0xFF191C20)
internal val SurfaceVariantLight = Color(0xFFDFE2EB)
internal val OnSurfaceVariantLight = Color(0xFF43474E)
internal val OutlineLight = Color(0xFF73777F)
internal val OutlineVariantLight = Color(0xFFC3C6CF)
internal val SurfaceDimLight = Color(0xFFD9DADF)
internal val SurfaceBrightLight = Color(0xFFF8F9FF)
internal val SurfaceContainerLowestLight = Color(0xFFFFFFFF)
internal val SurfaceContainerLowLight = Color(0xFFF2F3F9)
internal val SurfaceContainerLight = Color(0xFFEDEDF3)
internal val SurfaceContainerHighLight = Color(0xFFE7E8EE)
internal val SurfaceContainerHighestLight = Color(0xFFE1E2E8)

internal val SurfaceDark = Color(0xFF111318)
internal val OnSurfaceDark = Color(0xFFE2E2E9)
internal val SurfaceVariantDark = Color(0xFF43474E)
internal val OnSurfaceVariantDark = Color(0xFFC3C7CF)
internal val OutlineDark = Color(0xFF8D9199)
internal val OutlineVariantDark = Color(0xFF43474E)
internal val SurfaceDimDark = Color(0xFF111318)
internal val SurfaceBrightDark = Color(0xFF37393E)
internal val SurfaceContainerLowestDark = Color(0xFF0C0E14)
internal val SurfaceContainerLowDark = Color(0xFF1A1B20)
internal val SurfaceContainerDark = Color(0xFF1E2024)
internal val SurfaceContainerHighDark = Color(0xFF282A2F)
internal val SurfaceContainerHighestDark = Color(0xFF33353A)

internal val ErrorLight = Color(0xFFBA1A1A)
internal val OnErrorLight = Color(0xFFFFFFFF)
internal val ErrorDark = Color(0xFFFFB4AB)
internal val OnErrorDark = Color(0xFF690005)

/** Guard Cat launcher palette — stays brand-blue even when dynamic color is on. */
object XrayBrand {
    val BlueLight = Color(0xFF42A5F5)
    val Blue = Color(0xFF1976D2)
    val BlueDeep = Color(0xFF0D47A1)
    val CatEyeAmber = Color(0xFFFFC107)
    val CatEyeGreen = Color(0xFF4CAF50)
}
