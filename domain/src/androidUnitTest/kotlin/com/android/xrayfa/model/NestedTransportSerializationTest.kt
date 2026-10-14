package com.android.xrayfa.model

import com.android.xrayfa.config.GsonXrayConfigEncoder
import com.android.xrayfa.config.KotlinxXrayConfigEncoder
import com.android.xrayfa.model.stream.*
import com.google.gson.Gson
import kotlinx.serialization.json.*
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class NestedTransportSerializationTest {
    @Test
    fun bothEncodersPreserveJsonTypesAndUseEnabled() {
        val extra = Json.parseToJsonElement("""{"noSSEHeader":true,"xmux":{"maxConnections":3},"headers":{"X-Test":"a=b&c"},"downloadSettings":{"network":"xhttp"}}""").jsonObject
        val mask = Json.parseToJsonElement("""{"password":"secret"}""").jsonObject
        val config = XrayConfiguration(
            inbounds = emptyList(),
            outbounds = listOf(OutboundObject(
                tag = "proxy", protocol = "vless", settings = NoneOutboundConfigurationObject(),
                mux = MuxObject(enabled = false),
                streamSettings = StreamSettingsObject(network = "xhttp",
                    xhttpSettings = XHttpSettings(extra = extra),
                    finalMask = FinalMask(udp = listOf(MaskObject("salamander", mask)),
                        quicParams = buildJsonObject { put("congestion", "bbr"); put("debug", false) }),
                ),
            )),
        )
        val outputs = listOf(GsonXrayConfigEncoder(Gson()).encode(config), KotlinxXrayConfigEncoder().encode(config))
            .map { Json.parseToJsonElement(it) }
        assertEquals(outputs[0], outputs[1])
        val outbound = outputs[0].jsonObject["outbounds"]!!.jsonArray[0].jsonObject
        val stream = outbound["streamSettings"]!!.jsonObject
        assertEquals(extra, stream["xhttpSettings"]!!.jsonObject["extra"])
        assertEquals(mask, stream["finalMask"]!!.jsonObject["udp"]!!.jsonArray[0].jsonObject["settings"])
        assertFalse(outbound["mux"]!!.jsonObject["enabled"]!!.jsonPrimitive.boolean)
        assertNull(outbound["mux"]!!.jsonObject["enable"])
    }
}
