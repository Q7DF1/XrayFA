package com.android.xrayfa.parser

import com.android.xrayfa.common.core.CoreStartOptions
import com.android.xrayfa.common.routing.Rule
import com.android.xrayfa.common.routing.RoutingMode
import com.android.xrayfa.common.routing.encodeRules
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
    fun routeMode_migratesSavedChinaPresetToIndependentRuntimeRules() {
        val legacy = Rule(
            domain = listOf("geosite:cn", "geosite:geolocation-cn"),
            ip = listOf("geoip:cn"),
            outboundTag = "direct",
            ruleTag = "Bypass Mainland China",
        )
        val custom = legacy.copy(ruleTag = "Custom AND rule", port = "443")
        val parser = factory.getParser(ShareLinks.VLESS)
        val routing = parser.getBaseRoutingObject(ParserTestFixtures.settings.copy(
            routingMode = RoutingMode.ROUTE.code,
            routingRules = encodeRules(listOf(custom, legacy)),
        ))
        val rules = routing.rules!!
        assertEquals(4, rules.size)
        assertEquals("dns-out", rules[0].outboundTag)
        assertEquals(custom.toRuleObject(), rules[1])
        assertEquals(legacy.copy(ip = null).toRuleObject(), rules[2])
        assertEquals(legacy.copy(domain = null, ruleTag = "Bypass Mainland China IP").toRuleObject(), rules[3])
    }

    @Test
    fun routeMode_repairsSavedSplitRulesWithDuplicateTags() {
        val domain = Rule(
            domain = listOf("geosite:cn", "geosite:geolocation-cn"),
            outboundTag = "direct",
            ruleTag = "Bypass Mainland China",
        )
        val ip = domain.copy(domain = null, ip = listOf("geoip:cn"))
        val rules = factory.getParser(ShareLinks.VLESS).getBaseRoutingObject(
            ParserTestFixtures.settings.copy(
                routingMode = RoutingMode.ROUTE.code,
                routingRules = encodeRules(listOf(domain, ip)),
            ),
        ).rules!!
        assertEquals("dns-out", rules.first().outboundTag)
        assertEquals(listOf(domain.toRuleObject(), ip.copy(ruleTag = "Bypass Mainland China IP").toRuleObject()), rules.drop(1))
        val tags = rules.mapNotNull { it.ruleTag }
        assertEquals(tags.size, tags.toSet().size)
    }

    @Test
    fun deviceDnsIsInterceptedBeforeOtherRulesInEveryRoutingMode() {
        val dnsOverride = Rule(
            inboundTag = listOf("tun"),
            port = "53",
            outboundTag = "direct",
        )
        val parser = factory.getParser(ShareLinks.VLESS)
        val globalRules = parser.getBaseRoutingObject(
            ParserTestFixtures.settings.copy(routingMode = RoutingMode.GLOBAL.code),
        ).rules!!
        val routeRules = parser.getBaseRoutingObject(
            ParserTestFixtures.settings.copy(
                routingMode = RoutingMode.ROUTE.code,
                routingRules = encodeRules(listOf(dnsOverride)),
            ),
        ).rules!!
        val tcpOnlyRules = parser.getBaseRoutingObject(
            ParserTestFixtures.settings.copy(routingMode = RoutingMode.GLOBAL.code),
            tcpOnlyProxy = true,
        ).rules!!

        listOf(globalRules, routeRules, tcpOnlyRules).forEach { rules ->
            assertEquals("dns-out", rules.first().outboundTag)
            assertEquals(listOf("tun"), rules.first().inboundTag)
            assertEquals("53", rules.first().port)
            assertEquals(1, rules.count {
                it.inboundTag == listOf("tun") && it.port == "53" && it.outboundTag == "dns-out"
            })
        }
    }

    @Test
    fun generatedConfigUsesGoogleAndCloudflareDohWithBootstrapHosts() = runBlocking {
        val root = json.parseToJsonElement(
            factory.getParser(ShareLinks.VLESS).parse(CoreStartOptions(url = ShareLinks.VLESS)),
        ).jsonObject
        val dns = root["dns"]!!.jsonObject
        val servers = dns["servers"]!!.jsonArray.map { it.jsonPrimitive.content }
        val hosts = dns["hosts"]!!.jsonObject

        assertEquals(
            listOf("https://dns.google/dns-query", "https://cloudflare-dns.com/dns-query"),
            servers,
        )
        assertEquals(listOf("8.8.8.8", "8.8.4.4"), hosts["full:dns.google"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals(listOf("1.1.1.1", "1.0.0.1"), hosts["full:cloudflare-dns.com"]!!.jsonArray.map { it.jsonPrimitive.content })
    }

    @Test
    fun generatedConfigUsesOnlyUserConfiguredDohServers() = runBlocking {
        val settings = ParserTestFixtures.settings.copy(
            dnsOverHttpsServers = "https://dns.quad9.net/dns-query, https://dns.adguard.com/dns-query",
            ipV6Enable = true,
        )
        val root = json.parseToJsonElement(
            ParserTestFixtures.factory(settings).getParser(ShareLinks.VLESS).parse(CoreStartOptions(url = ShareLinks.VLESS)),
        ).jsonObject
        val dns = root["dns"]!!.jsonObject

        assertEquals(
            listOf("https://dns.quad9.net/dns-query", "https://dns.adguard.com/dns-query"),
            dns["servers"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals("UseIP", dns["queryStrategy"]!!.jsonPrimitive.content)
        assertTrue(dns["servers"]!!.jsonArray.all { it.jsonPrimitive.content.startsWith("https://") })
        assertTrue("full:dns.google" !in dns["hosts"]!!.jsonObject)
        assertTrue("full:cloudflare-dns.com" !in dns["hosts"]!!.jsonObject)
    }

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
        assertTrue(ruleTags.contains("DNS Traffic"))
        assertTrue(!ruleTags.contains("DNS Direct (TCP-only proxy)"))
        assertTrue(ruleTags.contains("Block QUIC (TCP-only proxy)"))
        assertTrue(ruleTags.contains("UDP Direct (TCP-only proxy)"))
    }
}
