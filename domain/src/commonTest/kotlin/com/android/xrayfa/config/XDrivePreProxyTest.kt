package com.android.xrayfa.config

import kotlinx.serialization.json.*
import kotlin.test.*

class XDrivePreProxyTest {
    @Test fun attachesStorageWithoutChangingDefaultOutboundOrDroppingSocketSettings() {
        val source = """{"inbounds":[{"tag":"xrayfa-pre"}],"outbounds":[
            {"tag":"proxy","protocol":"vless","mux":{"enabled":true},"streamSettings":{
                "network":"xdrive","sockopt":{"tcpFastOpen":true},"xdriveSettings":{"service":"google-drive","secrets":["a","b","c"]}}},
            {"tag":"direct","protocol":"freedom"}],"routing":{"rules":[]}}"""
        val pre = Json.parseToJsonElement("""{"tag":"proxy","protocol":"socks","settings":{"servers":[{"address":"127.0.0.1","port":1080}]}}""").jsonObject
        val root = Json.parseToJsonElement(XDrivePreProxy.attach(source, pre)).jsonObject
        val outbounds = root["outbounds"]!!.jsonArray
        assertEquals("proxy", outbounds[0].jsonObject["tag"]!!.jsonPrimitive.content)
        val sockopt = outbounds[0].jsonObject["streamSettings"]!!.jsonObject["sockopt"]!!.jsonObject
        assertEquals(JsonPrimitive(true), sockopt["tcpFastOpen"])
        assertEquals(JsonPrimitive("xrayfa-pre-1"), sockopt["dialerProxy"])
        assertEquals(sockopt["dialerProxy"], outbounds.last().jsonObject["tag"])
        assertEquals(pre["settings"], outbounds.last().jsonObject["settings"])
        val original = Json.parseToJsonElement(source).jsonObject
        assertEquals(original["routing"], root["routing"])
        assertEquals(original["outbounds"]!!.jsonArray[0].jsonObject["streamSettings"]!!.jsonObject["xdriveSettings"],
            outbounds[0].jsonObject["streamSettings"]!!.jsonObject["xdriveSettings"])
    }

    @Test fun refusesToAttachToGenericJsonWithoutStorageTransport() {
        assertFailsWith<IllegalArgumentException> {
            XDrivePreProxy.attach("""{"outbounds":[{"protocol":"freedom"}]}""",
                buildJsonObject { put("protocol", "socks") })
        }
    }
}
