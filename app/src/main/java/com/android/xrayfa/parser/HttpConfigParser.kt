package com.android.xrayfa.parser

import com.android.xrayfa.XrayAppCompatFactory
import com.android.xrayfa.common.GEO_LITE
import com.android.xrayfa.common.repository.SettingsRepository
import com.android.xrayfa.dto.HttpConfig
import com.android.xrayfa.dto.Link
import com.android.xrayfa.dto.Node
import com.android.xrayfa.model.HttpOutboundConfigurationObject
import com.android.xrayfa.model.HttpSocksServerObject
import com.android.xrayfa.model.HttpSocksUserObject
import com.android.xrayfa.model.OutboundObject
import com.android.xrayfa.model.stream.StreamSettingsObject
import com.android.xrayfa.utils.Device
import com.google.gson.Gson
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parser for HTTP proxy outbounds.
 *
 * Supported share link formats:
 *  - http://host:port#remark                (no authentication)
 *  - http://user:pass@host:port#remark      (plain, percent-encoded credentials)
 *
 * See https://xtls.github.io/config/outbounds/http.html
 */
@Singleton
class HttpConfigParser
@Inject constructor(
    override val settingsRepo: SettingsRepository,
    override val gson: Gson
) : AbstractConfigParser<HttpOutboundConfigurationObject, HttpConfig>() {

    override fun decodeProtocol(url: String): HttpConfig {
        require(url.startsWith("http://")) { "Not a valid HTTP proxy URL" }
        return ProxyLinkUtils.decode(url) { remark, server, port, user, pass ->
            HttpConfig(
                remark = remark,
                server = server,
                port = if (port == -1) 8080 else port,
                username = user,
                password = pass
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
            remark = protocol.remark
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
                        users = users
                    )
                )
            ),
            streamSettings = StreamSettingsObject(
                network = "tcp"
            )
        )
    }

    override suspend fun preParse(link: Link): Node {
        val config = decodeProtocol(link.content)
        return Node(
            id = link.id,
            url = link.content,
            protocolPrefix = "http",
            subscriptionId = link.subscriptionId,
            port = config.port,
            address = config.server,
            selected = link.selected,
            remark = config.remark,
            countryISO = if (settingsRepo.settingsFlow.first().geoLiteInstall) {
                Device.getCountryISOFromIp(
                    geoPath = "${XrayAppCompatFactory.xrayPATH}/$GEO_LITE",
                    ip = config.server
                )
            } else ""
        )
    }
}
