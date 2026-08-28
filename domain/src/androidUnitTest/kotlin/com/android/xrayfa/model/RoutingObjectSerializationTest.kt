package com.android.xrayfa.model

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Locks RoutingObject kotlinx.serialization output to legacy Gson behavior.
 */
class RoutingObjectSerializationTest {

    private val gson = Gson()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
    }

    private val sampleRules = listOf(
        RuleObject(
            type = "field",
            outboundTag = "proxy",
            domain = listOf("geosite:google"),
            ruleTag = "Test",
        ),
    )

    private val sampleRouting = RoutingObject(
        domainStrategy = "IPIfNonMatch",
        rules = sampleRules,
    )

    @Test
    fun routingObject_gsonJson_decodesWithKotlinx() {
        assertEquals(sampleRouting, json.decodeFromString<RoutingObject>(gson.toJson(sampleRouting)))
    }

    @Test
    fun routingObject_kotlinxJson_decodesWithGson() {
        assertEquals(sampleRouting, gson.fromJson(json.encodeToString(RoutingObject.serializer(), sampleRouting), RoutingObject::class.java))
    }

    @Test
    fun ruleObject_list_gsonAndKotlinxInterchange() {
        val gsonJson = gson.toJson(sampleRules)
        val ruleListType = object : TypeToken<List<RuleObject>>() {}.type
        assertEquals(sampleRules, json.decodeFromString<List<RuleObject>>(gsonJson))
        assertEquals(sampleRules, gson.fromJson(json.encodeToString(ListSerializer(RuleObject.serializer()), sampleRules), ruleListType))
    }
}
