package com.android.v2rayForAndroidUI.parser

import com.android.v2rayForAndroidUI.model.DnsObject
import com.android.v2rayForAndroidUI.model.InboundObject
import com.android.v2rayForAndroidUI.model.LogObject
import com.android.v2rayForAndroidUI.model.NoneOutboundConfigurationObject
import com.android.v2rayForAndroidUI.model.OutboundObject
import com.android.v2rayForAndroidUI.model.RoutingObject
import com.android.v2rayForAndroidUI.model.RuleObject
import com.android.v2rayForAndroidUI.model.SniffingObject
import com.android.v2rayForAndroidUI.model.SocksInboundConfigurationObject

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
                "8.8.8.8"
            )
        )
    }


    fun getBaseRoutingObject(): RoutingObject {

        return RoutingObject(
                domainStrategy = "IPIfNonMatch",
                rules = listOf(
                    RuleObject(
                        outboundTag = "proxy",
                        domain = listOf("geosite:geolocation-!cn")
                    ),
                    RuleObject(
                        outboundTag = "direct",
                        domain = listOf("geosite:geolocation-cn")
                    )
                )
        )
    }


    abstract fun parse(link: String):String
}