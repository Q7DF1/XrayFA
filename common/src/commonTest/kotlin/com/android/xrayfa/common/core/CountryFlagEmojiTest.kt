package com.android.xrayfa.common.core

import kotlin.test.Test
import kotlin.test.assertEquals

class CountryFlagEmojiTest {

    @Test
    fun twoLetterIso_becomesRegionalIndicatorFlag() {
        assertEquals("🇺🇸", CountryFlagEmoji.fromIsoCode("US"))
        assertEquals("🇬🇧", CountryFlagEmoji.fromIsoCode("GB"))
        assertEquals("🇯🇵", CountryFlagEmoji.fromIsoCode("JP"))
    }

    @Test
    fun lowercaseIso_isUppercased() {
        assertEquals("🇨🇳", CountryFlagEmoji.fromIsoCode("cn"))
    }

    @Test
    fun invalidLength_returnsQuestionMark() {
        assertEquals("❓", CountryFlagEmoji.fromIsoCode(""))
        assertEquals("❓", CountryFlagEmoji.fromIsoCode("U"))
        assertEquals("❓", CountryFlagEmoji.fromIsoCode("USA"))
    }
}
