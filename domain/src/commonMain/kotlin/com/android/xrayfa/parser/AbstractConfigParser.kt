package com.android.xrayfa.parser

import com.android.xrayfa.common.core.CoreStartOptions
import com.android.xrayfa.common.core.GeoIpProvider
import com.android.xrayfa.common.repository.ConfigParserSettings
import com.android.xrayfa.common.repository.ConfigParserSettingsProvider
import com.android.xrayfa.datastore.DomainStrategy
import com.android.xrayfa.datastore.RoutingMode
import com.android.xrayfa.datastore.decodeRules
import com.android.xrayfa.datastore.defaultRoutes
import com.android.xrayfa.config.XrayConfigEncoder
import com.android.xrayfa.dto.ParseLinkInput
import com.android.xrayfa.dto.ParsedNode
import com.android.xrayfa.model.AbsOutboundConfigurationObject
import com.android.xrayfa.model.ApiObject
import com.android.xrayfa.model.DnsObject
import com.android.xrayfa.model.HttpInboundConfigurationObject
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

/**
 * An abstract parser that provides parsing of common structures.
 * The specific content of each protocol is implemented by its subclass parser.
 * This parser defines the parsing standard for JSON configuration files.
 */
abstract class AbstractConfigParser<T : AbsOutboundConfigurationObject, P> {

    private var apiEnable: Boolean = false

    abstract val settingsProvider: ConfigParserSettingsProvider

    abstract val geoIpProvider: GeoIpProvider

    abstract val configEncoder: XrayConfigEncoder

    var otherProtocolParser: ((String) -> OutboundObject<*>)? = null

    abstract fun decodeProtocol(url: String): P

    abstract fun encodeProtocol(protocol: P): String

    fun getBaseInboundConfig(settings: ConfigParserSettings): InboundObject {
        return InboundObject(
            listen = settings.socksListen,
            port = settings.socksPort,
            protocol = "socks",
            settings = SocksInboundConfigurationObject(
                auth = "password",
                accounts = listOf(
                    SocksInboundConfigurationObject.AccountObject(
                        user = settings.socksUserName,
                        pass = settings.socksPassword,
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
        )
    }

    fun getHttpInboundConfig(settings: ConfigParserSettings): InboundObject {
        return InboundObject(
            listen = "0.0.0.0",
            port = settings.httpPort,
            protocol = "http",
            settings = HttpInboundConfigurationObject(
                userLevel = 8,
            ),
            sniffing = SniffingObject(
                destOverride = listOf("http", "tls"),
                enabled = true,
            ),
            tag = "http",
        )
    }

    fun getInboundConfigs(settings: ConfigParserSettings): List<InboundObject> {
        val inbounds = mutableListOf(
            getBaseInboundConfig(settings),
        )
        if (settings.lanHttpProxyEnable) {
            inbounds.add(getHttpInboundConfig(settings))
        }
        inbounds.add(getAPIInboundConfig())
        inbounds.add(getTunInboundConfig())
        return inbounds
    }

    fun getTunInboundConfig(): InboundObject {
        return InboundObject(
            port = 0,
            protocol = "tun",
            settings = TunInboundConfigurationObject(
                name = "xray0",
                MTU = 1500,
                userLevel = 8,
            ),
            sniffing = SniffingObject(
                destOverride = listOf("http", "tls"),
                enabled = true,
                routeOnly = false,
            ),
            tag = "tun",
        )
    }

    fun getAPIInboundConfig(): InboundObject {
        return InboundObject(
            listen = "127.0.0.1",
            port = 10085,
            protocol = "dokodemo-door",
            settings = TunnelInboundConfigurationObject(
                address = "127.0.0.1",
            ),
            tag = "api",
        )
    }

    fun getBaseOutboundConfig(): OutboundObject<NoneOutboundConfigurationObject> {
        return OutboundObject(
            protocol = "freedom",
            tag = "direct",
            settings = NoneOutboundConfigurationObject(),
        )
    }

    fun getBaseLogObject(): LogObject {
        return LogObject(
            logLevel = "warning",
        )
    }

    suspend fun getBaseDnsConfig(): DnsObject {
        val settings = settingsProvider.getConfigParserSettings()
        val dnsV4 = settings.dnsIPv4.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val dns = if (settings.ipV6Enable) {
            val dnsV6 = settings.dnsIPv6.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            dnsV4 + dnsV6
        } else {
            dnsV4
        }
        return DnsObject(
            hosts = mapOf(
                "domain:googleapis.cn" to "googleapis.com",
            ),
            servers = dns,
            queryStrategy = if (settings.ipV6Enable) "UseIP" else "UseIPv4",
        )
    }

    fun getBaseRoutingObject(settings: ConfigParserSettings, tcpOnlyProxy: Boolean = false): RoutingObject {
        var rules: List<RuleObject>? = if (settings.routingMode == RoutingMode.GLOBAL.code) {
            getGlobalRules()
        } else {
            decodeRules(settings.routingRules).toRuleObjects()
        }

        if (rules != null && rules.any { it.outboundTag == null && it.balancerTag == null }) {
            rules = decodeRules(defaultRoutes).toRuleObjects()
        }

        if (tcpOnlyProxy) {
            rules = getTcpOnlyProxyRules() + (rules ?: emptyList())
        }

        return RoutingObject(
            domainStrategy = when (settings.domainStrategy) {
                DomainStrategy.ASIS.code -> "AsIs"
                DomainStrategy.IP_IF_NON_MATCH.code -> "IPIfNonMatch"
                DomainStrategy.IP_ON_DEMAND.code -> "IPOnDemand"
                else -> throw IllegalArgumentException("wrong domain strategy")
            },
            rules = rules,
        )
    }

    fun getTcpOnlyProxyRules(): List<RuleObject> {
        return listOf(
            RuleObject(
                type = "field",
                port = "53",
                outboundTag = "direct",
                ruleTag = "DNS Direct (TCP-only proxy)",
            ),
            RuleObject(
                type = "field",
                network = "udp",
                port = "443",
                outboundTag = "block",
                ruleTag = "Block QUIC (TCP-only proxy)",
            ),
            RuleObject(
                type = "field",
                network = "udp",
                outboundTag = "direct",
                ruleTag = "UDP Direct (TCP-only proxy)",
            ),
        )
    }

    fun getGlobalRules(): List<RuleObject> {
        return listOf(
            RuleObject(
                type = "field",
                inboundTag = listOf("api"),
                outboundTag = "api",
            ),
            RuleObject(
                type = "field",
                port = "443",
                network = "udp",
                outboundTag = "block",
            ),
            RuleObject(
                type = "field",
                outboundTag = "direct",
                ip = listOf("geoip:private"),
            ),
            RuleObject(
                type = "field",
                outboundTag = "direct",
                domain = listOf("geosite:private"),
            ),
            RuleObject(
                type = "field",
                port = "0-65535",
                outboundTag = "proxy",
            ),
        )
    }

    private fun getBaseAPIObject(): ApiObject {
        apiEnable = true
        return ApiObject(
            tag = "api",
            services = listOf(
                "StatsService",
            ),
        )
    }

    private fun getBasePolicyObject(): PolicyObject {
        return PolicyObject(
            system = SystemPolicyObject(
                statsOutboundUplink = true,
                statsOutboundDownlink = true,
                statsInboundUplink = true,
                statsInboundDownlink = true,
            ),
        )
    }

    suspend fun parse(startOptions: CoreStartOptions): String {
        val settings = settingsProvider.getConfigParserSettings()
        val outbound = parseOutbound(startOptions.url)
        val outbounds = mutableListOf<OutboundObject<*>>()

        val pre = parsePreNodeIfNeeded(startOptions)
        val next = parseNextNodeIfNeeded(startOptions)
        if (pre != null) {
            outbound.streamSettings?.sockopt = Sockopt(dialerProxy = pre.tag)
            outbounds.add(pre)
        }
        outbounds.add(outbound)
        if (next != null) {
            next.streamSettings?.sockopt = Sockopt(dialerProxy = outbound.tag)
            outbounds.add(next)
        }
        outbounds.add(getBaseOutboundConfig())
        outbounds.add(
            OutboundObject(
                protocol = "dns",
                tag = "dns-out",
                settings = NoneOutboundConfigurationObject(),
            ),
        )
        outbounds.add(
            OutboundObject(
                protocol = "freedom",
                tag = "api",
                settings = NoneOutboundConfigurationObject(),
            ),
        )
        outbounds.add(
            OutboundObject(
                protocol = "blackhole",
                tag = "block",
                settings = NoneOutboundConfigurationObject(),
            ),
        )
        val tcpOnlyProxy = outbound.protocol == "http"
        val xrayConfig = XrayConfiguration(
            stats = emptyMap(),
            api = getBaseAPIObject(),
            dns = getBaseDnsConfig(),
            log = getBaseLogObject(),
            policy = getBasePolicyObject(),
            inbounds = getInboundConfigs(settings),
            outbounds = outbounds,
            routing = getBaseRoutingObject(settings, tcpOnlyProxy),
        )
        return configEncoder.encode(xrayConfig)
    }

    fun parsePreNodeIfNeeded(startOptions: CoreStartOptions): OutboundObject<*>? {
        val outbound = startOptions.preUrl?.let {
            otherProtocolParser?.invoke(it)
        }
        outbound?.tag = "pre-node"
        return outbound
    }

    fun parseNextNodeIfNeeded(startOptions: CoreStartOptions): OutboundObject<*>? {
        val outbound = startOptions.nextUrl?.let {
            otherProtocolParser?.invoke(it)
        }
        outbound?.tag = "next-node"
        return outbound
    }

    @Throws(Exception::class)
    abstract fun parseOutbound(url: String): OutboundObject<T>

    @Throws(Exception::class)
    abstract suspend fun preParse(link: ParseLinkInput): ParsedNode

    protected suspend fun countryIsoForServer(ip: String): String {
        return if (settingsProvider.getConfigParserSettings().geoLiteInstall) {
            geoIpProvider.countryIsoFromIp(ip)
        } else {
            ""
        }
    }
}
