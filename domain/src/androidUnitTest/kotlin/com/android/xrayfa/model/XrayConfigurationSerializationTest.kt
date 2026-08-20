package com.android.xrayfa.model

import com.android.xrayfa.config.KotlinxXrayConfigEncoder
import com.android.xrayfa.config.GsonXrayConfigEncoder
import com.android.xrayfa.model.stream.RealitySettings
import com.android.xrayfa.model.stream.StreamSettingsObject
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Locks full XrayConfiguration kotlinx.serialization output to legacy Gson behavior.
 */
class XrayConfigurationSerializationTest {

    private val gson = Gson()
    private val gsonEncoder = GsonXrayConfigEncoder(gson)
    private val kotlinxEncoder = KotlinxXrayConfigEncoder()

    private val sampleConfig = XrayConfiguration(
        stats = emptyMap(),
        api = ApiObject(
            tag = "api",
            services = listOf("StatsService"),
        ),
        dns = DnsObject(
            hosts = mapOf("domain:googleapis.cn" to "googleapis.com"),
            servers = listOf("1.1.1.1", "8.8.8.8"),
            queryStrategy = "UseIPv4",
        ),
        log = LogObject(logLevel = "warning"),
        policy = PolicyObject(
            system = SystemPolicyObject(
                statsOutboundUplink = true,
                statsOutboundDownlink = true,
                statsInboundUplink = true,
                statsInboundDownlink = true,
            ),
        ),
        inbounds = listOf(
            InboundObject(
                listen = "127.0.0.1",
                port = 10808,
                protocol = "socks",
                settings = SocksInboundConfigurationObject(
                    auth = "password",
                    accounts = listOf(
                        SocksInboundConfigurationObject.AccountObject(
                            user = "xrayfa",
                            pass = "xrayfa",
                        ),
                    ),
                    udp = true,
                    userLevel = 8,
                ),
                sniffing = SniffingObject(
                    destOverride = listOf("http", "tls"),
                    enabled = true,
                ),
                tag = "socks",
            ),
        ),
        outbounds = listOf(
            OutboundObject(
                protocol = "vless",
                settings = VLESSOutboundConfigurationObject(
                    vnext = listOf(
                        ServerObject(
                            address = "example.com",
                            port = 443,
                            users = listOf(
                                UserObject(
                                    id = "00000000-0000-0000-0000-000000000001",
                                    encryption = "",
                                    flow = "",
                                    level = 0,
                                    security = "auto",
                                ),
                            ),
                        ),
                    ),
                ),
                streamSettings = StreamSettingsObject(
                    network = "raw",
                    security = "reality",
                    realitySettings = RealitySettings(
                        fingerprint = "chrome",
                        publicKey = "public-key",
                        serverName = "example.com",
                        spiderX = "",
                        shortId = "abcd",
                        show = false,
                    ),
                ),
                mux = MuxObject(
                    concurrency = -1,
                    enable = false,
                    xudpConcurrency = 8,
                    xudpProxyUDP443 = "",
                ),
                tag = "proxy",
            ),
            OutboundObject(
                protocol = "freedom",
                tag = "direct",
                settings = NoneOutboundConfigurationObject(),
            ),
        ),
        routing = RoutingObject(
            domainStrategy = "IPIfNonMatch",
            rules = listOf(
                RuleObject(
                    type = "field",
                    outboundTag = "proxy",
                    domain = listOf("geosite:google"),
                    ruleTag = "Test",
                ),
            ),
        ),
    )

    @Test
    fun xrayConfiguration_kotlinxJson_matchesGsonJson() {
        val gsonJson = gsonEncoder.encode(sampleConfig)
        val kotlinxJson = kotlinxEncoder.encode(sampleConfig)
        assertEquals(gson.fromJson(gsonJson, Map::class.java), gson.fromJson(kotlinxJson, Map::class.java))
    }
}
