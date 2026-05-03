package com.android.xrayfa.parser

import com.android.xrayfa.common.repository.DomainStrategy
import com.android.xrayfa.common.repository.RoutingMode
import com.android.xrayfa.common.repository.Rule
import com.android.xrayfa.common.repository.SettingsRepository
import com.android.xrayfa.common.repository.SettingsState
import com.android.xrayfa.core.StartOptions
import com.android.xrayfa.dto.Link
import com.android.xrayfa.dto.Node
import com.android.xrayfa.model.AbsOutboundConfigurationObject
import com.android.xrayfa.model.ApiObject
import com.android.xrayfa.model.DnsObject
import com.android.xrayfa.model.InboundObject
import com.android.xrayfa.model.LogObject
import com.android.xrayfa.model.NoneOutboundConfigurationObject
import com.android.xrayfa.model.OutboundObject
import com.android.xrayfa.model.PolicyObject
import com.android.xrayfa.model.RoutingObject
import com.android.xrayfa.model.RuleObject
import com.android.xrayfa.model.SniffingObject
import com.android.xrayfa.model.Sockopt
import com.android.xrayfa.model.SocksInboundConfigurationObject
import com.android.xrayfa.model.SystemPolicyObject
import com.android.xrayfa.model.TunInboundConfigurationObject
import com.android.xrayfa.model.TunnelInboundConfigurationObject
import com.android.xrayfa.model.XrayConfiguration
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.first


/**
 *
 * An abstract parser that provides parsing of common structures.
 * The specific content of each protocol is implemented by its subclass parser.
 * This parser defines the parsing standard for JSON configuration files.
 *
 */

abstract class AbstractConfigParser<T: AbsOutboundConfigurationObject,P>(
) {

    private var apiEnable: Boolean = false

    abstract val settingsRepo: SettingsRepository

    abstract val gson: Gson

    var otherProtocolParser: ((String) -> OutboundObject<*>)? = null

    abstract fun decodeProtocol(url: String): P

    abstract fun encodeProtocol(protocol: P): String
    fun getBaseInboundConfig(settingsState: SettingsState): InboundObject {
        return InboundObject(
            listen = settingsState.socksListen,
            port = settingsState.socksPort,
            protocol = "socks",
            settings = SocksInboundConfigurationObject(
                auth = "password",  //password auth instead of "noauth"
                accounts = listOf(SocksInboundConfigurationObject.AccountObject(
                    user = settingsState.socksUserName,
                    pass = settingsState.socksPassword
                )),
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


    fun getBaseRoutingObject(settingsState: SettingsState): RoutingObject {
        val rules = if (settingsState.routingMode == RoutingMode.GLOBAL) {
            getGlobalRules()
        }else {
            val targetType = object : TypeToken<List<RuleObject?>?>() {}.type
            gson.fromJson(settingsState.routingRules, targetType)
        }
        return RoutingObject(
            domainStrategy = when(settingsState.domainStrategy) {
                DomainStrategy.ASIS -> "AsIs"
                DomainStrategy.IP_IF_NON_MATCH -> "IPIfNonMatch"
                DomainStrategy.IP_ON_DEMAND -> "IPOnDemand"
                else -> throw IllegalArgumentException("wrong domain strategy")
            },
            rules = rules
        )

    }

    fun getGlobalRules(): List<RuleObject> {
        return listOf(
            RuleObject(
                type = "field",
                inboundTag = listOf("api"),
                outboundTag = "api"
            ),
            RuleObject(
                type = "field",
                port = "443",
                network = "udp",
                outboundTag = "block"
            ),
            RuleObject(
                type = "field",
                outboundTag = "direct",
                ip = listOf("geoip:private")
            ),
            RuleObject(
                type = "field",
                outboundTag = "direct",
                domain = listOf("geosite:private")
            ),
            RuleObject(
                type = "field",
                port = "0-65535",
                outboundTag = "proxy"
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

    suspend fun parse(startOptions: StartOptions):String {
        val settingsState = settingsRepo.settingsFlow.first()
        val outbound = parseOutbound(startOptions.url)
        val outbounds = mutableListOf<OutboundObject<*>>()

        val pre = parsePreNodeIfNeeded(startOptions)
        val next = parseNextNodeIfNeeded(startOptions)
        if (pre != null) {
            //pre.proxySettings = ProxySettingsObject(tag = outbound.tag)
            outbound.streamSettings?.sockopt = Sockopt(dialerProxy = pre.tag)
            outbounds.add(pre)
        }
        outbounds.add(outbound)
        if (next != null) {
            //outbound.proxySettings = ProxySettingsObject(tag = next.tag)
            next.streamSettings?.sockopt = Sockopt(dialerProxy = outbound.tag)
            outbounds.add(next)
        }
        outbounds.add(getBaseOutboundConfig())
        outbounds.add(
            OutboundObject(
            protocol = "dns",
            tag = "dns-out",
            settings = NoneOutboundConfigurationObject()
        ))
        outbounds.add(
            OutboundObject(
                protocol = "freedom",
                tag = "api",
                settings = NoneOutboundConfigurationObject()
            )
        )
        outbounds.add(
            OutboundObject(
                protocol = "blackhole",
                tag = "block",
                settings = NoneOutboundConfigurationObject()
            )
        )
        val xrayConfig = XrayConfiguration(
            stats = emptyMap(), // enable
            api = getBaseAPIObject(),
            dns = getBaseDnsConfig(),
            log = getBaseLogObject(),
            policy = getBasePolicyObject(),
            inbounds = listOf(
                getBaseInboundConfig(settingsState),
                getAPIInboundConfig(),
                getTunInboundConfig()
            ),
            outbounds = outbounds,
            routing = getBaseRoutingObject(settingsState),
        )
        val config = Gson().toJson(xrayConfig)
        println(config)
        return config
    }

    fun parsePreNodeIfNeeded(startOptions: StartOptions): OutboundObject<*>? {
        val outbound = startOptions.preUrl?.let {
            otherProtocolParser?.invoke(it)
        }
        outbound?.tag = "pre-node"
        return outbound
    }

    fun parseNextNodeIfNeeded(startOptions: StartOptions): OutboundObject<*>? {
        val outbound = startOptions.nextUrl?.let {
            otherProtocolParser?.invoke(it)
        }
        outbound?.tag = "next-node"
        return outbound
    }

    @Throws(Exception::class)
    abstract fun parseOutbound(url: String): OutboundObject<T>
    @Throws(Exception::class)
    abstract suspend fun preParse(link: Link): Node
}