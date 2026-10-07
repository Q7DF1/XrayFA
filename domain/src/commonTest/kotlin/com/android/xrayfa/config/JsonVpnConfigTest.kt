package com.android.xrayfa.config

import kotlinx.serialization.json.*
import kotlin.test.*

class JsonVpnConfigTest {
    private val source = """{"outbounds":[{"protocol":"freedom","tag":"main","future":{"keep":true}}],"dns":{"servers":["https://example.org/dns-query"]},"routing":{"rules":[]},"custom":{"keep":42}}"""
    private val transport = JsonVpnTransport(socksPort = 12345)

    @Test fun preservesUserFieldsAndPreparesDnsChannel() {
        val original = Json.parseToJsonElement(source).jsonObject
        val runtime = Json.parseToJsonElement(JsonVpnConfig.prepare(source, transport)).jsonObject
        assertEquals(original["dns"], runtime["dns"])
        assertEquals(original["custom"], runtime["custom"])
        assertEquals(original["outbounds"]!!.jsonArray[0], runtime["outbounds"]!!.jsonArray[0])
        assertNotNull(runtime["stats"])
        assertEquals("12345", runtime["inbounds"]!!.jsonArray[0].jsonObject["port"]!!.jsonPrimitive.content)
        val dnsRule = runtime["routing"]!!.jsonObject["rules"]!!.jsonArray[0].jsonObject
        assertEquals("198.18.0.1", dnsRule["ip"]!!.jsonArray[0].jsonPrimitive.content)
    }

    @Test fun rejectsDuplicateKeysAndDeepOrOversizedDocuments() {
        assertFailsWith<JsonConfigException> { JsonVpnConfig.inspect("""{"outbounds":[],"outbounds":[{"protocol":"freedom"}]}""") }
        assertFailsWith<JsonConfigException> { JsonVpnConfig.inspect("[".repeat(65) + "0" + "]".repeat(65)) }
        assertFailsWith<JsonConfigException> { JsonVpnConfig.inspect(" ".repeat(JsonVpnConfig.MAX_BYTES + 1)) }
    }

    @Test fun requiresExplicitMappingForInboundRulesAndKeepsTheirMeaning() {
        val text = """{"outbounds":[{"protocol":"freedom"}],"inbounds":[{"tag":"client","protocol":"socks","port":9000}],"routing":{"rules":[{"inboundTag":["client"],"outboundTag":"direct","type":"field"}]}}"""
        assertFailsWith<JsonConfigException> { JsonVpnConfig.prepare(text, transport) }
        val runtime = Json.parseToJsonElement(JsonVpnConfig.prepare(text, transport, "client")).jsonObject
        assertEquals("client", runtime["inbounds"]!!.jsonArray[0].jsonObject["tag"]!!.jsonPrimitive.content)
        assertEquals(Json.parseToJsonElement(text).jsonObject["routing"]!!.jsonObject["rules"]!!.jsonArray[0], runtime["routing"]!!.jsonObject["rules"]!!.jsonArray[1])
    }

    @Test fun avoidsTagCollisionsAndRejectsListeningConflicts() {
        val text = """{"outbounds":[{"protocol":"freedom","tag":"xrayfa-dns"}],"inbounds":[{"protocol":"http","port":12345,"listen":"0.0.0.0"}] }"""
        assertFailsWith<JsonConfigException> { JsonVpnConfig.prepare(text, transport) }
        assertFailsWith<JsonConfigException> { JsonVpnConfig.prepare(text.replace("12345", "\"12000-13000\""), transport) }
        val root = Json.parseToJsonElement(JsonVpnConfig.prepare(source.replace("main", "xrayfa-dns"), transport)).jsonObject
        assertEquals("xrayfa-dns-1", root["outbounds"]!!.jsonArray.last().jsonObject["tag"]!!.jsonPrimitive.content)
    }

    @Test fun nativeTunDoesNotAddSocksAndPreparationIsRepeatable() {
        val t = transport.copy(nativeTun = true)
        assertEquals(JsonVpnConfig.prepare(source, t), JsonVpnConfig.prepare(source, t))
        val root = Json.parseToJsonElement(JsonVpnConfig.prepare(source, t)).jsonObject
        assertEquals("tun", root["inbounds"]!!.jsonArray[0].jsonObject["protocol"]!!.jsonPrimitive.content)
        assertNull(root["api"])
    }

    @Test fun mappingRemovesRemoteTransportAndKeepsSniffing() {
        val text = """{"inbounds":[{"tag":"input","protocol":"vless","listen":"0.0.0.0","port":443,"streamSettings":{"security":"tls"},"sniffing":{"enabled":true}}],"outbounds":[{"protocol":"freedom"}]}"""
        val root = Json.parseToJsonElement(JsonVpnConfig.prepare(text, transport, "input")).jsonObject
        val inbound = root["inbounds"]!!.jsonArray.single().jsonObject
        assertNull(inbound["streamSettings"])
        assertEquals("127.0.0.1", inbound["listen"]!!.jsonPrimitive.content)
        assertEquals(true, inbound["sniffing"]!!.jsonObject["enabled"]!!.jsonPrimitive.boolean)
        assertFailsWith<JsonConfigException> { JsonVpnConfig.inspect("""{"outbounds":[{"protocol":"freedom"}],"stats":null}""") }
        assertFailsWith<JsonConfigException> { JsonVpnConfig.prepare("""{"outbounds":[{"protocol":"freedom"}],"routing":{"rules":[null]}}""", transport) }
    }
}
