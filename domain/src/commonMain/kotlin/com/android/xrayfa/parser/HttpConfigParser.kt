package com.android.xrayfa.parser

import com.android.xrayfa.common.core.GeoIpProvider
import com.android.xrayfa.common.repository.ConfigParserSettingsProvider
import com.android.xrayfa.config.XrayConfigEncoder
import com.android.xrayfa.dto.HttpConfig
import com.android.xrayfa.dto.ParseLinkInput
import com.android.xrayfa.dto.ParsedNode
import com.android.xrayfa.model.HttpOutboundConfigurationObject
import com.android.xrayfa.model.HttpSocksServerObject
import com.android.xrayfa.model.HttpSocksUserObject
import com.android.xrayfa.model.OutboundObject
import com.android.xrayfa.model.stream.StreamSettingsObject

/**
 * Parser for HTTP proxy outbounds.
 */
class HttpConfigParser(
    override val settingsProvider: ConfigParserSettingsProvider,
    override val geoIpProvider: GeoIpProvider,
    override val configEncoder: XrayConfigEncoder,
) : AbstractConfigParser<HttpOutboundConfigurationObject, HttpConfig>() {

    override fun decodeProtocol(url: String): HttpConfig {
        require(url.startsWith("http://")) { "Not a valid HTTP proxy URL" }
        return ProxyLinkUtils.decode(url) { remark, server, port, user, pass ->
            HttpConfig(
                remark = remark,
                server = server,
                port = if (port == -1) 8080 else port,
                username = user,
                password = pass,
            )
        }
    }

    override fun encodeProtocol(protocol: HttpConfig): String {
        return ProxyLinkUtils.encode(
            scheme = "http",
            server = protocol.server,
            port = protocol.port,
            username = protocol.username,
            password = protocol.password,
            remark = protocol.remark,
        )
    }

    override fun parseOutbound(url: String): OutboundObject<HttpOutboundConfigurationObject> {
        val config = decodeProtocol(url)
        val users = if (!config.username.isNullOrEmpty()) {
            listOf(HttpSocksUserObject(user = config.username, pass = config.password ?: ""))
        } else null
        return OutboundObject(
            tag = "proxy",
            protocol = "http",
            settings = HttpOutboundConfigurationObject(
                servers = listOf(
                    HttpSocksServerObject(
                        address = config.server,
                        port = config.port,
                        users = users,
                    ),
                ),
            ),
            streamSettings = StreamSettingsObject(
                network = "tcp",
            ),
        )
    }

    override suspend fun preParse(link: ParseLinkInput): ParsedNode {
        val config = decodeProtocol(link.content)
        return ParsedNode(
            id = link.id,
            url = link.content,
            protocolPrefix = "http",
            subscriptionId = link.subscriptionId,
            port = config.port,
            address = config.server,
            selected = link.selected,
            remark = config.remark,
            countryISO = countryIsoForServer(config.server),
        )
    }
}
