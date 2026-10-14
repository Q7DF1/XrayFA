package com.android.xrayfa.parser

import com.android.xrayfa.common.utils.Base64Compat
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Xray-core rejected `allowInsecure: true` after 2026-06-01.
 * Share-link query `allowInsecure=1` must not reach runtime JSON.
 */
class AllowInsecureNotEmittedTest {

    private val factory = ParserTestFixtures.factory()

    @Test
    fun vlessTls_allowInsecureQuery_isRejected() {
        val url =
            "vless://00000000-0000-0000-0000-000000000001@example.com:443" +
                "?type=tcp&security=tls&encryption=none&allowInsecure=1#n"
        assertOutboundHasNoAllowInsecureTrue(url)
    }

    @Test
    fun vmessTls_allowInsecureJson_isRejected() {
        val payload =
            """{"v":"2","ps":"n","add":"example.com","port":443,"id":"00000000-0000-0000-0000-000000000001","aid":0,"net":"tcp","tls":"tls","allowInsecure":"1"}"""
        val url = "vmess://${Base64Compat.encode(payload.encodeToByteArray())}"
        assertOutboundHasNoAllowInsecureTrue(url)
    }

    @Test
    fun trojanTls_allowInsecureQuery_isRejected() {
        val url = "trojan://password@example.com:443?type=tcp&security=tls&allowInsecure=1#n"
        assertOutboundHasNoAllowInsecureTrue(url)
    }

    @Test
    fun hysteria2_allowInsecureQuery_isRejected() {
        val url = "hysteria2://auth-token@example.com:443?alpn=h3&allowInsecure=1#n"
        assertOutboundHasNoAllowInsecureTrue(url)
    }

    private fun assertOutboundHasNoAllowInsecureTrue(url: String) {
        val error = assertFailsWith<IllegalArgumentException> {
            factory.getParser(url).parseOutbound(url)
        }
        assertTrue(error.message.orEmpty().contains("no longer supports"))
    }
}
