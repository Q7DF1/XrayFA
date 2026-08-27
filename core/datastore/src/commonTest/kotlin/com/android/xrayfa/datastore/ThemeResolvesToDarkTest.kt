package com.android.xrayfa.datastore

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThemeResolvesToDarkTest {

    @Test
    fun lightMode_isNeverDark() {
        assertFalse(Theme.LIGHT_MODE.resolvesToDark(systemDark = true))
        assertFalse(Theme.LIGHT_MODE.resolvesToDark(systemDark = false))
    }

    @Test
    fun darkMode_isAlwaysDark() {
        assertTrue(Theme.DARK_MODE.resolvesToDark(systemDark = true))
        assertTrue(Theme.DARK_MODE.resolvesToDark(systemDark = false))
    }

    @Test
    fun autoMode_followsSystem() {
        assertTrue(Theme.AUTO_MODE.resolvesToDark(systemDark = true))
        assertFalse(Theme.AUTO_MODE.resolvesToDark(systemDark = false))
    }

    @Test
    fun fromCode_unknownFallsBackToAuto() {
        assertTrue(Theme.fromCode(99).resolvesToDark(systemDark = true))
        assertFalse(Theme.fromCode(99).resolvesToDark(systemDark = false))
    }
}
