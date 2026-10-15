package com.android.xrayfa.parser

import com.android.xrayfa.common.utils.Base64Compat
import com.android.xrayfa.common.utils.UrlCodec
import com.android.xrayfa.dto.VLESSConfig
import kotlinx.serialization.json.*
import kotlin.test.*

class ShareLinkConfigRegressionTest {
    private val factory = ParserTestFixtures.factory()
    private val id = "00000000-0000-0000-0000-000000000001"

    private fun outbound(url: String) = Json.parseToJsonElement(
        ParserTestFixtures.encodeOutbound(factory.getParser(url).parseOutbound(url)),
    ).jsonObject
    private fun stream(url: String) = outbound(url).getValue("streamSettings").jsonObject

    @Test
    fun ipv6AndEscapedValuesRoundTripWithoutDoubleDecoding() {
        val params = mapOf("type" to "xhttp", "path" to "/a?x=1&y=%2F+#", "extra" to """{"headers":{"X-Test":"a=b&c+d%"},"noSSEHeader":true}""")
        val original = VLESSConfig(remark = "name + % #", uuid = id, server = "2001:db8::1", port = 443, param = params)
        val encoded = factory.vlessConfigParser.encodeProtocol(original)
        val decoded = factory.vlessConfigParser.decodeProtocol(encoded)
        assertEquals(original, decoded)
        assertEquals("2001:db8::1", outbound(encoded)["settings"]!!.jsonObject["vnext"]!!.jsonArray[0].jsonObject["address"]!!.jsonPrimitive.content)
    }

    @Test
    fun trojanPasswordIsDecodedExactlyOnce() {
        val link = "trojan://a%2540b%2B%26%3D@example.com?type=ws&path=%2Fa%3Fx%3D1%26y%3D2#name%20%2B"
        val decoded = factory.trojanConfigParser.decodeProtocol(link)
        assertEquals("a%40b+&=", decoded.password)
        assertEquals("/a?x=1&y=2", decoded.params["path"])
        assertEquals(443, decoded.port)
        assertEquals(decoded.copy(original = ""), factory.trojanConfigParser.decodeProtocol(factory.trojanConfigParser.encodeProtocol(decoded)).copy(original = ""))
    }

    @Test
    fun legacyFormEncodingKeepsSpacesAndLiteralCredentialPlus() {
        val extra = """{ "headers": { "X-Test": "a + b" } }"""
        val link = "trojan://a+b@example.com?type=xhttp&extra=${UrlCodec.encode(extra)}#node+name%2B"
        val decoded = factory.trojanConfigParser.decodeProtocol(link)
        assertEquals("a+b", decoded.password)
        assertEquals("node name+", decoded.remark)
        assertEquals(extra, decoded.params["extra"])
        assertEquals(Json.parseToJsonElement(extra), stream(link)["xhttpSettings"]!!.jsonObject["extra"])
    }

    @Test
    fun vlessDefaultsEncryptionAndUsesSniSeparateFromHost() {
        val json = outbound("vless://$id@192.0.2.1:443?type=ws&security=tls&sni=cert.example&host=cdn.example&alpn=h2,http%2F1.1&fp=chrome&pcs=abcd&vcn=cert.example")
        val user = json["settings"]!!.jsonObject["vnext"]!!.jsonArray[0].jsonObject["users"]!!.jsonArray[0].jsonObject
        assertEquals("none", user["encryption"]!!.jsonPrimitive.content)
        val stream = json["streamSettings"]!!.jsonObject
        val tls = stream["tlsSettings"]!!.jsonObject
        assertEquals("cert.example", tls["serverName"]!!.jsonPrimitive.content)
        assertEquals("abcd", tls["pinnedPeerCertSha256"]!!.jsonPrimitive.content)
        assertEquals("cert.example", tls["verifyPeerCertByName"]!!.jsonPrimitive.content)
        assertEquals(listOf("h2", "http/1.1"), tls["alpn"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals("cdn.example", stream["wsSettings"]!!.jsonObject["host"]!!.jsonPrimitive.content)
        assertEquals("/", stream["wsSettings"]!!.jsonObject["path"]!!.jsonPrimitive.content)
        assertNull(stream["wsSettings"]!!.jsonObject["headers"])
        assertFalse(json["mux"]!!.jsonObject["enabled"]!!.jsonPrimitive.boolean)
        assertNull(json["mux"]!!.jsonObject["enable"])
    }

    @Test
    fun xhttpExtraPreservesNestedValuesAndAliases() {
        val extra = """{"xmux":{"maxConnections":3},"noSSEHeader":true,"downloadSettings":{"network":"xhttp","xhttpSettings":{"path":"/download"}},"headers":{"X-Test":"a=b&c"}}"""
        val settings = stream("vless://$id@example.com:443?type=splithttp&extra=${UrlCodec.encode(extra)}")
        assertEquals("xhttp", settings["network"]!!.jsonPrimitive.content)
        val xhttp = settings["xhttpSettings"]!!.jsonObject
        assertEquals("auto", xhttp["mode"]!!.jsonPrimitive.content)
        assertEquals(Json.parseToJsonElement(extra), xhttp["extra"])
        assertFailsWith<IllegalArgumentException> { stream("vless://$id@example.com:443?type=xhttp&mode=splitHttp") }
        assertFailsWith<IllegalArgumentException> { stream("vless://$id@example.com:443?type=xhttp&extra=%5B%5D") }
    }

    @Test
    fun realityUsesPasswordAndPreservesAdditionalParameters() {
        val reality = stream("vless://$id@example.com:443?security=reality&pbk=key&sid=ab&spx=%2Fa%3Fx%3D1&pqv=verify")["realitySettings"]!!.jsonObject
        assertEquals("key", reality["password"]!!.jsonPrimitive.content)
        assertEquals("chrome", reality["fingerprint"]!!.jsonPrimitive.content)
        assertEquals("/a?x=1", reality["spiderX"]!!.jsonPrimitive.content)
        assertEquals("verify", reality["mldsa65Verify"]!!.jsonPrimitive.content)
    }

    @Test
    fun hysteriaObfsUsesObjectMaskAndSni() {
        val settings = stream("hy2://a%40b%2B%3D@192.0.2.1?obfs=salamander&obfs-password=secret%2B%26&sni=cert.example&alpn=h3,h2")
        val tls = settings["tlsSettings"]!!.jsonObject
        assertEquals("cert.example", tls["serverName"]!!.jsonPrimitive.content)
        assertEquals(2, tls["alpn"]!!.jsonArray.size)
        val mask = settings["finalMask"]!!.jsonObject["udp"]!!.jsonArray.single().jsonObject
        assertEquals("salamander", mask["type"]!!.jsonPrimitive.content)
        assertEquals("secret+&", mask["settings"]!!.jsonObject["password"]!!.jsonPrimitive.content)
        assertEquals("a@b+=", settings["hysteriaSettings"]!!.jsonObject["auth"]!!.jsonPrimitive.content)
        assertNull(stream(ShareLinks.HYSTERIA2)["finalMask"])
        assertFailsWith<IllegalArgumentException> { stream("hy2://auth@example.com?obfs=salamander") }
        assertFailsWith<IllegalArgumentException> { stream("hy2://auth@example.com?obfs=unknown") }
    }

    @Test
    fun vmessOnlyEmitsHttpCamouflageWhenRequested() {
        fun link(header: String) = "vmess://" + Base64Compat.encode(
            """{"add":"192.0.2.1","port":443,"id":"$id","net":"tcp","type":"$header","host":"cdn.example","path":"/one,/two","tls":"tls","sni":"cert.example"}""".encodeToByteArray(),
        )
        val plain = stream(link("none"))
        assertEquals("none", plain["rawSettings"]!!.jsonObject["header"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("cert.example", plain["tlsSettings"]!!.jsonObject["serverName"]!!.jsonPrimitive.content)
        val http = stream(link("http"))["rawSettings"]!!.jsonObject["header"]!!.jsonObject
        assertEquals("http", http["type"]!!.jsonPrimitive.content)
        assertEquals(listOf("/one", "/two"), http["request"]!!.jsonObject["path"]!!.jsonArray.map { it.jsonPrimitive.content })
    }

    @Test
    fun trojanSupportsXhttpRealityAndHttpUpgrade() {
        val xhttp = stream("trojan://password@example.com?type=xhttp&security=reality&pbk=key&fp=chrome")
        assertNotNull(xhttp["xhttpSettings"])
        assertNotNull(xhttp["realitySettings"])
        assertNull(xhttp["tlsSettings"])
        val upgrade = stream("trojan://password@example.com?type=httpupgrade&host=cdn.example&path=%2Fup&sni=cert.example")
        assertEquals("/up", upgrade["httpUpgradeSettings"]!!.jsonObject["path"]!!.jsonPrimitive.content)
        assertEquals("cert.example", upgrade["tlsSettings"]!!.jsonObject["serverName"]!!.jsonPrimitive.content)
    }

    @Test
    fun invalidEndpointsAndRemovedTransportsFailExplicitly() {
        for (link in listOf("vless://$id@example.com:0", "vless://$id@example.com:65536", "vless://@example.com:443")) {
            assertFailsWith<IllegalArgumentException> { stream(link) }
        }
        for (transport in listOf("h2", "h3", "http", "quic", "unknown")) {
            assertFailsWith<IllegalArgumentException> { stream("vless://$id@example.com:443?type=$transport") }
        }
        assertFailsWith<IllegalArgumentException> { stream("vless://$id@example.com:443?security=xtls") }
        for (transport in listOf("ws", "httpupgrade", "kcp")) {
            assertFailsWith<IllegalArgumentException> { stream("vless://$id@example.com:443?type=$transport&security=reality&pbk=key") }
        }
    }
}
