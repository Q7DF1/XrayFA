package com.android.xrayfa.common.core

import kotlin.test.Test
import kotlin.test.assertEquals

class GeoIpCountryDisplayTest {

    @Test
    fun missingLookup_isEmptyString() {
        assertEquals("", GeoIpCountryDisplay.fromIsoCode(null))
    }

    @Test
    fun successfulIso_isFlagEmoji() {
        assertEquals("🇺🇸", GeoIpCountryDisplay.fromIsoCode("US"))
    }

    @Test
    fun emptyIso_isQuestionMark() {
        assertEquals("❓", GeoIpCountryDisplay.fromIsoCode(""))
    }
}
