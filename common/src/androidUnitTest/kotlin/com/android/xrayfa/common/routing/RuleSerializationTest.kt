package com.android.xrayfa.common.routing

import com.android.xrayfa.common.json.decodeStringList
import com.android.xrayfa.common.json.encodeStringList
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Locks kotlinx.serialization output to legacy Gson behavior for routing rules
 * and package lists stored in DataStore.
 */
class RuleSerializationTest {

    private val gson = Gson()
    private val ruleListType = object : TypeToken<List<Rule>>() {}.type

    @Test
    fun defaultRoutes_gsonCanParseKotlinxOutput() {
        val rules: List<Rule> = gson.fromJson(defaultRoutes, ruleListType)
        assertEquals(defaultRouteList, rules)
    }

    @Test
    fun encodeRules_roundTrip_matchesInput() {
        val original = defaultRouteList
        assertEquals(original, decodeRules(encodeRules(original)))
    }

    @Test
    fun encodeRules_gsonLegacyJson_canBeDecodedByKotlinx() {
        val gsonJson = gson.toJson(defaultRouteList)
        assertEquals(defaultRouteList, decodeRules(gsonJson))
    }

    @Test
    fun encodeRules_kotlinxJson_canBeDecodedByGson() {
        val kotlinxJson = encodeRules(defaultRouteList)
        val gsonRules: List<Rule> = gson.fromJson(kotlinxJson, ruleListType)
        assertEquals(defaultRouteList, gsonRules)
    }

    @Test
    fun customRule_gsonAndKotlinxInterchange() {
        val custom = listOf(
            Rule(
                type = "field",
                outboundTag = "proxy",
                domain = listOf("geosite:google"),
                port = "443",
                ruleTag = "Custom",
            ),
        )
        assertEquals(custom, decodeRules(gson.toJson(custom)))
        assertEquals(custom, gson.fromJson(encodeRules(custom), ruleListType))
    }

    @Test
    fun encodeStringList_matchesGson() {
        val packages = listOf("com.example.a", "com.example.b")
        assertEquals(gson.toJson(packages), encodeStringList(packages))
        assertEquals(packages, decodeStringList(gson.toJson(packages)))
    }

    @Test
    fun encodeStringList_emptyArray() {
        assertEquals("[]", encodeStringList(emptyList()))
        assertEquals(emptyList<String>(), decodeStringList("[]"))
    }
}
