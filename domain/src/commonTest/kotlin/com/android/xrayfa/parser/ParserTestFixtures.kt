package com.android.xrayfa.parser

import com.android.xrayfa.common.core.GeoIpProvider
import com.android.xrayfa.common.repository.ConfigParserSettings
import com.android.xrayfa.common.repository.ConfigParserSettingsProvider
import com.android.xrayfa.config.KotlinxXrayConfigEncoder
import com.android.xrayfa.config.XrayJson
import com.android.xrayfa.common.routing.DomainStrategy
import com.android.xrayfa.common.routing.RoutingMode
import com.android.xrayfa.model.OutboundObject
import com.android.xrayfa.model.serialization.OutboundObjectSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlin.test.assertEquals

internal object ParserTestFixtures {
    // Dummy local SOCKS auth for parser unit tests only. Not a real credential.
    private const val TEST_LOCAL_SOCKS_AUTH = "xrayfa"

    val settings = ConfigParserSettings(
        socksListen = "127.0.0.1",
        socksPort = 10808,
        socksUserName = TEST_LOCAL_SOCKS_AUTH,
        socksPassword = TEST_LOCAL_SOCKS_AUTH,
        lanHttpProxyEnable = false,
        httpPort = 10809,
        dnsIPv4 = "1.1.1.1,8.8.8.8",
        dnsIPv6 = "",
        ipV6Enable = false,
        domainStrategy = DomainStrategy.IP_IF_NON_MATCH.code,
        routingRules = "",
        routingMode = RoutingMode.GLOBAL.code,
        geoLiteInstall = false,
    )

    val settingsProvider = object : ConfigParserSettingsProvider {
        override suspend fun getConfigParserSettings(): ConfigParserSettings = settings
    }

    val geoIpProvider = object : GeoIpProvider {
        override fun countryIsoFromIp(ip: String): String = ""
    }

    val encoder = KotlinxXrayConfigEncoder()

    fun factory(): ParserFactory = ParserFactory(
        vlessConfigParser = VLESSConfigParser(settingsProvider, geoIpProvider, encoder),
        vmessConfigParser = VMESSConfigParser(settingsProvider, geoIpProvider, encoder),
        trojanConfigParser = TrojanConfigParser(settingsProvider, geoIpProvider, encoder),
        shadowSocksConfigParser = ShadowSocksConfigParser(settingsProvider, geoIpProvider, encoder),
        hysteria2ConfigParser = Hysteria2ConfigParser(settingsProvider, geoIpProvider, encoder),
        socksConfigParser = SocksConfigParser(settingsProvider, geoIpProvider, encoder),
        httpConfigParser = HttpConfigParser(settingsProvider, geoIpProvider, encoder),
    )

    fun encodeOutbound(outbound: OutboundObject<*>): String =
        XrayJson.encodeToString(OutboundObjectSerializer, outbound)

    fun assertJsonEquals(expected: String, actual: String) {
        assertEquals(parseJson(expected), parseJson(actual))
    }

    private fun parseJson(value: String): JsonElement =
        Json.parseToJsonElement(value)
}
