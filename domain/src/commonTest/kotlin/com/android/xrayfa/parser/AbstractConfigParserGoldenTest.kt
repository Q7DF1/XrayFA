package com.android.xrayfa.parser

import com.android.xrayfa.common.core.CoreStartOptions
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AbstractConfigParserGoldenTest {

    private val factory = ParserTestFixtures.factory()
    private val json = Json

    @Test
    fun vless_parse_matchesFullConfigGolden() = runBlocking {
        val actual = factory.getParser(ShareLinks.VLESS).parse(CoreStartOptions(url = ShareLinks.VLESS))
        ParserTestFixtures.assertJsonEquals(VLESS_FULL_CONFIG_GOLDEN, actual)
    }

    @Test
    fun parse_chainsPreAndNextNodes() = runBlocking {
        val actual = factory.getParser(ShareLinks.VLESS).parse(
            CoreStartOptions(
                url = ShareLinks.VLESS,
                preUrl = ShareLinks.SOCKS,
                nextUrl = ShareLinks.TROJAN,
            ),
        )
        val outbounds = json.parseToJsonElement(actual).jsonObject["outbounds"]!!.jsonArray
        val tags = outbounds.map { it.jsonObject["tag"]!!.jsonPrimitive.content }
        assertEquals(
            listOf("pre-node", "proxy", "next-node", "direct", "dns-out", "api", "block"),
            tags,
        )
        val proxy = outbounds.first { it.jsonObject["tag"]!!.jsonPrimitive.content == "proxy" }
        assertEquals(
            "pre-node",
            proxy.jsonObject["streamSettings"]!!
                .jsonObject["sockopt"]!!
                .jsonObject["dialerProxy"]!!
                .jsonPrimitive.content,
        )
        val next = outbounds.first { it.jsonObject["tag"]!!.jsonPrimitive.content == "next-node" }
        assertEquals(
            "proxy",
            next.jsonObject["streamSettings"]!!
                .jsonObject["sockopt"]!!
                .jsonObject["dialerProxy"]!!
                .jsonPrimitive.content,
        )
        assertEquals("socks", outbounds[0].jsonObject["protocol"]!!.jsonPrimitive.content)
        assertEquals("trojan", outbounds[2].jsonObject["protocol"]!!.jsonPrimitive.content)
    }

    @Test
    fun http_parse_addsTcpOnlyProxyRules() = runBlocking {
        val actual = factory.getParser(ShareLinks.HTTP).parse(CoreStartOptions(url = ShareLinks.HTTP))
        val rules = json.parseToJsonElement(actual).jsonObject["routing"]!!.jsonObject["rules"]!!.jsonArray
        val ruleTags = rules.mapNotNull { it.jsonObject["ruleTag"]?.jsonPrimitive?.content }
        assertTrue(ruleTags.contains("DNS Direct (TCP-only proxy)"))
        assertTrue(ruleTags.contains("Block QUIC (TCP-only proxy)"))
        assertTrue(ruleTags.contains("UDP Direct (TCP-only proxy)"))
    }
}
