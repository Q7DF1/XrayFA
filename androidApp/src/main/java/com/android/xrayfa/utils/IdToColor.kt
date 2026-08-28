package com.android.xrayfa.utils

import androidx.compose.ui.graphics.Color

val ColorMap = mapOf(
    1 to Color.Red.copy(alpha = 0.3f),
    2 to Color.Blue.copy(alpha = 0.3f),
    3 to Color.Green.copy(alpha = 0.3f),
    4 to Color.Yellow.copy(alpha = 0.3f),
    5 to Color.Cyan.copy(alpha = 0.3f),
    6 to Color.Magenta.copy(alpha = 0.3f),
    7 to Color.Gray.copy(alpha = 0.3f),
    8 to Color.Black.copy(alpha = 0.1f),
    9 to Color.White.copy(alpha = 0.4f),
).withDefault { key->  Color.LightGray.copy(alpha = 0.3f)}