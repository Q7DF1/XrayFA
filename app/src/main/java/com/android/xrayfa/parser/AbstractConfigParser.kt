package com.android.xrayfa.parser

import com.android.xrayfa.common.repository.SettingsRepository
import com.android.xrayfa.model.AbsOutboundConfigurationObject
import com.android.xrayfa.model.ApiObject
import com.android.xrayfa.model.DnsObject
import com.android.xrayfa.model.InboundObject
import com.android.xrayfa.model.Link
import com.android.xrayfa.model.LogObject
import com.android.xrayfa.model.Node
import com.android.xrayfa.model.NoneOutboundConfigurationObject
import com.android.xrayfa.model.OutboundObject
import com.android.xrayfa.model.PolicyObject
import com.android.xrayfa.model.RoutingObject
import com.android.xrayfa.model.RuleObject
import com.android.xrayfa.model.SniffingObject
import com.android.xrayfa.model.SocksInboundConfigurationObject
import com.android.xrayfa.model.SystemPolicyObject
import com.android.xrayfa.model.TunnelInboundConfigurationObject
import com.android.xrayfa.model.XrayConfiguration
import com.google.gson.Gson
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 *
 * An abstract parser that provides parsing of common structures.
 * The specific content of each protocol is implemented by its subclass parser.
 * This parser defines the parsing standard for JSON configuration files.
 *
 */

abstract class AbstractConfigParser<T: AbsOutboundConfigurationObject> {

    private var apiEnable: Boolean = false

    protected lateinit var settingsRepo: SettingsRepository

    suspend fun getBaseInboundConfig(): InboundObject {
        val settingsState = settingsRepo.settingsFlow.first()
        return InboundObject(
            listen = "127.0.0.1",
            port = settingsState.socksPort,
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

    fun getBaseOutboundConfig(): OutboundObject<NoneOutboundConfigurationObject> {

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

    suspend fun parse(link: String):String {

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
    abstract fun parseOutbound(link: String): OutboundObject<T>
    @Throws(Exception::class)
    abstract fun preParse(link: Link): Node
}