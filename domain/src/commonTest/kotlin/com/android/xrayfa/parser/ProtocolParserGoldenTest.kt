package com.android.xrayfa.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ProtocolParserGoldenTest {

    private val factory = ParserTestFixtures.factory()

    @Test
    fun vless_shareLink_matchesOutboundGolden() {
        assertOutbound(ShareLinks.VLESS, ParserOutboundGoldens.VLESS)
    }

    @Test
    fun vmess_shareLink_matchesOutboundGolden() {
        assertOutbound(ShareLinks.vmess, ParserOutboundGoldens.VMESS)
    }

    @Test
    fun trojan_shareLink_matchesOutboundGolden() {
        assertOutbound(ShareLinks.TROJAN, ParserOutboundGoldens.TROJAN)
    }

    @Test
    fun shadowsocks_shareLink_matchesOutboundGolden() {
        assertOutbound(ShareLinks.shadowsocks, ParserOutboundGoldens.SHADOWSOCKS)
    }

    @Test
    fun hysteria2_shareLink_matchesOutboundGolden() {
        assertOutbound(ShareLinks.HYSTERIA2, ParserOutboundGoldens.HYSTERIA2)
    }

    @Test
    fun socks_shareLink_matchesOutboundGolden() {
        assertOutbound(ShareLinks.SOCKS, ParserOutboundGoldens.SOCKS)
    }

    @Test
    fun http_shareLink_matchesOutboundGolden() {
        assertOutbound(ShareLinks.HTTP, ParserOutboundGoldens.HTTP)
    }

    @Test
    fun socks5_scheme_usesSocksParser() {
        val url = "socks5://user:pass@example.com:1080#socks-node"
        val outbound = factory.getParser(url).parseOutbound(url)
        ParserTestFixtures.assertJsonEquals(ParserOutboundGoldens.SOCKS, ParserTestFixtures.encodeOutbound(outbound))
    }

    @Test
    fun unknownProtocol_throws() {
        val error = assertFailsWith<IllegalArgumentException> {
            factory.getParser("wireguard://example")
        }
        assertEquals("Unsupported protocol: wireguard", error.message)
    }

    private fun assertOutbound(url: String, expectedJson: String) {
        val outbound = factory.getParser(url).parseOutbound(url)
        ParserTestFixtures.assertJsonEquals(expectedJson, ParserTestFixtures.encodeOutbound(outbound))
    }
}
