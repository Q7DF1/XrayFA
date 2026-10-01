package com.android.xrayfa.shared.ui.nav

import kotlin.math.roundToInt

fun snapTabIndex(value: Float, tabsCount: Int): Int {
    val last = (tabsCount - 1).coerceAtLeast(0)
    return value.roundToInt().coerceIn(0, last)
}
