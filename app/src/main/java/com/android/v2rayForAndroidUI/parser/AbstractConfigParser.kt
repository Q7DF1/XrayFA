package com.android.v2rayForAndroidUI.parser

import com.android.v2rayForAndroidUI.model.DnsObject
import com.android.v2rayForAndroidUI.model.InboundObject
import com.android.v2rayForAndroidUI.model.LogObject
import com.android.v2rayForAndroidUI.model.MuxObject
import com.android.v2rayForAndroidUI.model.NoneOutboundConfigurationObject
import com.android.v2rayForAndroidUI.model.OutboundObject
import com.android.v2rayForAndroidUI.model.RoutingObject
import com.android.v2rayForAndroidUI.model.RuleObject
import com.android.v2rayForAndroidUI.model.ServerObject
import com.android.v2rayForAndroidUI.model.SniffingObject
import com.android.v2rayForAndroidUI.model.SocksInboundConfigurationObject
import com.android.v2rayForAndroidUI.model.UserObject
import com.android.v2rayForAndroidUI.model.VLESSOutboundConfigurationObject
import com.android.v2rayForAndroidUI.model.XrayConfiguration
import com.android.v2rayForAndroidUI.model.stream.RawSettings
import com.android.v2rayForAndroidUI.model.stream.RealitySettings
import com.android.v2rayForAndroidUI.model.stream.StreamSettingsObject
import com.google.gson.Gson

/**
 * TODO configuration 从这里构造，outbound 由子类提供即可
 */
abstract class AbstractConfigParser {


    fun getBaseInboundConfig(): InboundObject {
        return InboundObject(
            listen = "127.0.0.1",
            port = 10808,
            protocol = "socks",
            settings = SocksInboundConfigurationObject(
                auth = "noauth",
                udp = true,
                userLevel = 8
            ),
            sniffing = SniffingObject(
                destOverride = listOf("http","tls"),
                enabled = true
            ),
            tag = "socks"
        )
    }

    fun getBaseOutboundConfig(): OutboundObject {

        return OutboundObject(
            protocol = "freedom",
            tag = "direct",
            settings = NoneOutboundConfigurationObject()
        )
    }

    fun getBaseLogObject(): LogObject {
        return LogObject(
            logLevel = "warning"
        )
    }


    fun getBaseDnsConfig(): DnsObject {
        return DnsObject(
            hosts = mapOf(
                "domain:googleapis.cn" to "googleapis.com"
            ),
            servers = listOf(
                "8.8.8.8",
                "1.1.1.1"
            )
        )
    }


    fun getBaseRoutingObject(): RoutingObject {

        return RoutingObject(
                domainStrategy = "IPIfNonMatch",
                rules = listOf(
                    RuleObject(
                        type = "field",
                        outboundTag = "proxy",
                        domain = listOf("geosite:geolocation-!cn")
                    ),
                    RuleObject(
                        type = "field",
                        outboundTag = "direct",
                        domain = listOf("giegeolocation-cn")
                    )
                )
        )
    }


    fun parse(link: String):String {

        val vlessConfig = XrayConfiguration(
            dns = getBaseDnsConfig(),
            log = getBaseLogObject(),
            inbounds = listOf(getBaseInboundConfig()),
            outbounds = listOf(
                getBaseOutboundConfig(),
                parseOutbound(link)
            ),
            routing = getBaseRoutingObject(),
        )
        val config = Gson().toJson(vlessConfig)
        println(config)
        return config
    }

    abstract fun parseOutbound(link: String): OutboundObject
}