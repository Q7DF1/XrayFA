package com.android.xrayfa.shared.config

import com.android.xrayfa.common.core.GeoIpProvider
import com.android.xrayfa.common.repository.ConfigParserSettings
import com.android.xrayfa.common.repository.ConfigParserSettingsProvider
import com.android.xrayfa.config.KotlinxXrayConfigEncoder
import com.android.xrayfa.parser.HttpConfigParser
import com.android.xrayfa.parser.Hysteria2ConfigParser
import com.android.xrayfa.parser.ParserFactory
import com.android.xrayfa.parser.ShadowSocksConfigParser
import com.android.xrayfa.parser.SocksConfigParser
import com.android.xrayfa.parser.TrojanConfigParser
import com.android.xrayfa.parser.VLESSConfigParser
import com.android.xrayfa.parser.VMESSConfigParser

internal fun testParserFactory(): ParserFactory {
    val settings = object : ConfigParserSettingsProvider {
        override suspend fun getConfigParserSettings() = ConfigParserSettings(
            socksListen = "127.0.0.1", socksPort = 10808, socksUserName = "test", socksPassword = "test",
            lanHttpProxyEnable = false, httpPort = 10809,
            ipV6Enable = false, domainStrategy = 0, routingRules = "", routingMode = 0, geoLiteInstall = false,
        )
    }
    val geo = object : GeoIpProvider { override fun countryIsoFromIp(ip: String) = "" }
    val encoder = KotlinxXrayConfigEncoder()
    return ParserFactory(
        VLESSConfigParser(settings, geo, encoder), VMESSConfigParser(settings, geo, encoder),
        TrojanConfigParser(settings, geo, encoder), ShadowSocksConfigParser(settings, geo, encoder),
        Hysteria2ConfigParser(settings, geo, encoder), SocksConfigParser(settings, geo, encoder),
        HttpConfigParser(settings, geo, encoder),
    )
}
