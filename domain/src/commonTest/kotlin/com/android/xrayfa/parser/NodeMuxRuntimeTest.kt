package com.android.xrayfa.parser

import com.android.xrayfa.common.core.CoreStartOptions
import com.android.xrayfa.config.NodeMuxConfig
import com.android.xrayfa.model.MuxObject
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.*

class NodeMuxRuntimeTest {
    private val factory = ParserTestFixtures.factory()
    private val id = "00000000-0000-0000-0000-000000000001"
    private val link = "vless://$id@example.com:443?security=tls"
    private fun mux(tcp: Int, udp: Int = 8) = NodeMuxConfig.encode(MuxObject(concurrency = tcp, xudpConcurrency = udp))
    private suspend fun config(options: CoreStartOptions) = Json.parseToJsonElement(factory.getParser(options.url).parse(options)).jsonObject

    @Test fun absentMuxIsOmittedAndEachChainHopUsesItsOwnConfiguration() = runBlocking {
        assertNull(config(CoreStartOptions(link))["outbounds"]!!.jsonArray.first().jsonObject["mux"])
        val outbounds = config(CoreStartOptions(link, preUrl = link, nextUrl = link,
            muxSettings = mux(4), preMuxSettings = mux(2), nextMuxSettings = mux(16)))["outbounds"]!!.jsonArray
        val byTag = outbounds.associate { it.jsonObject["tag"]!!.jsonPrimitive.content to it.jsonObject }
        for ((tag, expected) in mapOf("proxy" to 4, "pre-node" to 2, "next-node" to 16)) {
            assertEquals(expected, byTag[tag]!!["mux"]!!.jsonObject["concurrency"]!!.jsonPrimitive.int)
        }
        assertNull(byTag["direct"]!!["mux"])
    }

    @Test fun visionKeepsXudpAndDisablesTcpMux() = runBlocking {
        val node = "$link&flow=xtls-rprx-vision"
        val result = config(CoreStartOptions(node, muxSettings = mux(8, 32)))["outbounds"]!!.jsonArray.first().jsonObject["mux"]!!.jsonObject
        assertTrue(result["enabled"]!!.jsonPrimitive.boolean)
        assertEquals(-1, result["concurrency"]!!.jsonPrimitive.int)
        assertEquals(32, result["xudpConcurrency"]!!.jsonPrimitive.int)
    }

    @Test fun unsupportedTransportAndProtocolDoNotApplyStoredMux() = runBlocking {
        for (url in listOf("$link&type=xhttp", "hysteria2://password@example.com:443?sni=example.com")) {
            assertNull(config(CoreStartOptions(url, muxSettings = mux(8)))["outbounds"]!!.jsonArray.first().jsonObject["mux"])
        }
    }

    @Test fun httpWithMuxUdpDoesNotInstallTcpOnlyBypassRules() = runBlocking {
        val settings = ParserTestFixtures.settings
        val parser = ParserTestFixtures.factory(settings).getParser("http://example.com:8080")
        val json = Json.parseToJsonElement(parser.parse(CoreStartOptions("http://example.com:8080", muxSettings = mux(8)))).jsonObject
        val rules = json["routing"]!!.jsonObject["rules"]!!.jsonArray
        assertFalse(rules.any { it.jsonObject["ruleTag"]?.jsonPrimitive?.content == "UDP Direct (TCP-only proxy)" })
    }
}
