package com.android.xrayfa.common.routing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RegionRoutingTest {
    @Test fun removesRetiredGulfPresetButPreservesCustomVariations() {
        val retired = Rule(ip = listOf("geoip:ae", "geoip:sa", "geoip:qa", "geoip:kw", "geoip:bh", "geoip:om"),
            outboundTag = "direct", ruleTag = "region:gcc:ip")
        val custom = retired.copy(port = "443")
        assertEquals(listOf(custom) + chinaDirectRules,
            decodeRules(encodeRules(listOf(retired, custom) + chinaDirectRules)))
    }

    @Test fun presetsUseSeparateDomainAndIpRules() {
        val ru = RegionRoutingPreset.RUSSIA.rules
        assertEquals(listOf("geosite:category-ru"), ru[0].domain)
        assertEquals(listOf("geoip:ru"), ru[1].ip)
        assertTrue(RegionRoutingPreset.entries.flatMap { it.rules }.all { it.domain == null || it.ip == null })
        assertEquals(chinaDirectRules, RegionRoutingPreset.CHINA.rules)
    }

    @Test fun customRulesReferencingCountriesRemainCustom() {
        val custom = Rule(domain = listOf("geosite:cn"), outboundTag = "proxy", ruleTag = "My China")
        assertFalse(isRegionPresetRule(custom))
        assertFalse(isRegionPresetRule(chinaDirectRules.first().copy(port = "443")))
        assertTrue(chinaDirectRules.all(::isRegionPresetRule))
    }

    @Test fun togglingPreservesCustomRulesAndOtherCountries() {
        val custom = Rule(domain = listOf("example.com"), outboundTag = "proxy")
        val rules = listOf(custom) + chinaDirectRules
        val enabled = RegionRoutingPreset.RUSSIA.setEnabled(rules, true)
        assertEquals(enabled, RegionRoutingPreset.RUSSIA.setEnabled(enabled, true))
        assertEquals(rules, RegionRoutingPreset.RUSSIA.setEnabled(enabled, false))
        assertEquals(enabled, decodeRules(encodeRules(enabled)))
    }
}
