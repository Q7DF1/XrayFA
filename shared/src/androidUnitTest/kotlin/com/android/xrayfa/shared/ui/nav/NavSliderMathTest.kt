package com.android.xrayfa.shared.ui.nav

import kotlin.test.Test
import kotlin.test.assertEquals

class NavSliderMathTest {
    @Test
    fun snapsToNearestTab() {
        assertEquals(0, snapTabIndex(0.4f, 2))
        assertEquals(1, snapTabIndex(0.5f, 2))
        assertEquals(1, snapTabIndex(1.4f, 2))
    }

    @Test
    fun clampsToRange() {
        assertEquals(0, snapTabIndex(-1f, 2))
        assertEquals(1, snapTabIndex(3f, 2))
    }
}
