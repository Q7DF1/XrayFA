package com.android.xrayfa.parser

import com.android.xrayfa.common.repository.SettingsRepository
import com.android.xrayfa.model.AbsOutboundConfigurationObject
import com.android.xrayfa.model.ApiObject
import com.android.xrayfa.model.DnsObject
import com.android.xrayfa.model.InboundObject
import com.android.xrayfa.dto.Link
import com.android.xrayfa.model.LogObject
import com.android.xrayfa.dto.Node
import com.android.xrayfa.model.NoneOutboundConfigurationObject
import com.android.xrayfa.model.OutboundObject
import com.android.xrayfa.model.PolicyObject
import com.android.xrayfa.model.RoutingObject
import com.android.xrayfa.model.RuleObject
import com.android.xrayfa.model.SniffingObject
import com.android.xrayfa.model.SocksInboundConfigurationObject
import com.android.xrayfa.model.SystemPolicyObject
import com.android.xrayfa.model.TunInboundConfigurationObject
import com.android.xrayfa.model.TunnelInboundConfigurationObject
import com.android.xrayfa.model.XrayConfiguration
import com.google.gson.Gson
import kotlinx.coroutines.flow.first

/**
 *
 * An abstract parser that provides parsing of common structures.
 * The specific content of each protocol is implemented by its subclass parser.
 * This parser defines the parsing standard for JSON configuration files.
 *
 */

abstract class AbstractConfigParser<T: AbsOutboundConfigurationObject,P> {

    private var apiEnable: Boolean = false

    abstract val settingsRepo: SettingsRepository


    abstract fun decodeProtocol(url: String): P

    abstract fun encodeProtocol(protocol: P): String
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

    suspend fun getTunInboundConfig(): InboundObject {
        return InboundObject(
            port = 0,
            protocol = "tun",
            settings = TunInboundConfigurationObject(
                name ="xray0",
                MTU =1500,
                userLevel = 8
            ),
            sniffing = SniffingObject(
                destOverride = listOf("http","tls"),
                enabled = true,
                routeOnly = false
            ),
            tag = "tun"
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


    suspend fun getBaseDnsConfig(): DnsObject {
        val settingsState = settingsRepo.settingsFlow.first()
        val dnsV4 = settingsState.dnsIPv4.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        var dns: List<String>
        if (settingsState.ipV6Enable) {
            val dnsV6 = settingsState.dnsIPv6.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            dns = dnsV4 + dnsV6
        }else {
            dns = dnsV4
        }
        return DnsObject(
            hosts = mapOf(
                "domain:googleapis.cn" to "googleapis.com"
            ),
            servers = dns
        )
    }


    fun getBaseRoutingObject(): RoutingObject {

        return RoutingObject(
                domainStrategy = "IPIfNonMatch",
                rules = listOf(
                    RuleObject(
                        type = "field",
                        outboundTag = "proxy",
                        domain = listOf("geosite:telegram"),
                    ),
                    RuleObject(
                        type = "field",
                        outboundTag = "proxy",
                        ip = listOf("geoip:telegram")
                    ),
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
                    ),
                    RuleObject(
                        inboundTag = arrayListOf("tun"),
                        outboundTag = "dns-out",
                        port = "53"
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
            inbounds = listOf(
                getBaseInboundConfig(),
                getAPIInboundConfig(),
                getTunInboundConfig()
            ),
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
    abstract fun parseOutbound(url: String): OutboundObject<T>
    @Throws(Exception::class)
    abstract suspend fun preParse(link: Link): Node
}