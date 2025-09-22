package com.android.v2rayForAndroidUI.parser

import com.android.v2rayForAndroidUI.model.ApiObject
import com.android.v2rayForAndroidUI.model.DnsObject
import com.android.v2rayForAndroidUI.model.InboundObject
import com.android.v2rayForAndroidUI.model.Link
import com.android.v2rayForAndroidUI.model.LogObject
import com.android.v2rayForAndroidUI.model.Node
import com.android.v2rayForAndroidUI.model.NoneOutboundConfigurationObject
import com.android.v2rayForAndroidUI.model.OutboundObject
import com.android.v2rayForAndroidUI.model.PolicyObject
import com.android.v2rayForAndroidUI.model.RoutingObject
import com.android.v2rayForAndroidUI.model.RuleObject
import com.android.v2rayForAndroidUI.model.SniffingObject
import com.android.v2rayForAndroidUI.model.SocksInboundConfigurationObject
import com.android.v2rayForAndroidUI.model.StatsObject
import com.android.v2rayForAndroidUI.model.SystemPolicyObject
import com.android.v2rayForAndroidUI.model.TunnelInboundConfigurationObject
import com.android.v2rayForAndroidUI.model.XrayConfiguration
import com.google.gson.Gson

/**
 * TODO configuration 从这里构造，outbound 由子类提供即可
 */
abstract class AbstractConfigParser {

    private var apiEnable: Boolean = false
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

    fun getAPIInboundConfig(): InboundObject {
        return InboundObject(
            listen = "127.0.0.1",
            port = 10085,
            protocol = "dokodemo-door",
            settings = TunnelInboundConfigurationObject(
                address = "127.0.0.1"
            ),
            tag = "api"
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
                        domain = listOf("geosite:geolocation-cn")
                    ),
                    RuleObject(
                        inboundTag = listOf("api"),
                        outboundTag = "api",
                        type = "field"
                    )
                )
        )
    }

    private fun getBaseAPIObject(): ApiObject {
        apiEnable = true
        return ApiObject(
            tag = "api",
            services = listOf(
                "StatsService"
            )
        )
    }

    private fun getBasePolicyObject(): PolicyObject {
        return PolicyObject(
            system = SystemPolicyObject(
                statsOutboundUplink = true,
                statsOutboundDownlink = true,
                statsInboundUplink = true,
                statsInboundDownlink = true
            )
        )
    }

    fun parse(link: String):String {

        val vlessConfig = XrayConfiguration(
            stats = emptyMap(), // enable
            api = getBaseAPIObject(),
            dns = getBaseDnsConfig(),
            log = getBaseLogObject(),
            policy = getBasePolicyObject(),
            inbounds = listOf(getBaseInboundConfig(),getAPIInboundConfig()),
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

    @Throws(Exception::class)
    abstract fun parseOutbound(link: String): OutboundObject
    @Throws(Exception::class)
    abstract fun preParse(link: Link): Node
}