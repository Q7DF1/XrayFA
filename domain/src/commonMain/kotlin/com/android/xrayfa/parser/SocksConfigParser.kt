package com.android.xrayfa.parser

import com.android.xrayfa.common.core.GeoIpProvider
import com.android.xrayfa.common.repository.ConfigParserSettingsProvider
import com.android.xrayfa.config.XrayConfigEncoder
import com.android.xrayfa.dto.ParseLinkInput
import com.android.xrayfa.dto.ParsedNode
import com.android.xrayfa.dto.SocksConfig
import com.android.xrayfa.model.HttpSocksServerObject
import com.android.xrayfa.model.HttpSocksUserObject
import com.android.xrayfa.model.OutboundObject
import com.android.xrayfa.model.SocksOutboundConfigurationObject
import com.android.xrayfa.model.stream.StreamSettingsObject
import com.android.xrayfa.common.utils.Base64Compat
import com.android.xrayfa.common.utils.UrlCodec

/**
 * Parser for SOCKS proxy outbounds.
 */
class SocksConfigParser(
    override val settingsProvider: ConfigParserSettingsProvider,
    override val geoIpProvider: GeoIpProvider,
    override val configEncoder: XrayConfigEncoder,
) : AbstractConfigParser<SocksOutboundConfigurationObject, SocksConfig>() {

    override fun decodeProtocol(url: String): SocksConfig {
        require(url.startsWith("socks://") || url.startsWith("socks5://")) { "Not a valid SOCKS URL" }
        return ProxyLinkUtils.decode(url) { remark, server, port, user, pass ->
            SocksConfig(
                remark = remark,
                server = server,
                port = if (port == -1) 1080 else port,
                username = user,
                password = pass,
            )
        }
    }

    override fun encodeProtocol(protocol: SocksConfig): String {
        return ProxyLinkUtils.encode(
            scheme = "socks",
            server = protocol.server,
            port = protocol.port,
            username = protocol.username,
            password = protocol.password,
            remark = protocol.remark,
        )
    }

    override fun parseOutbound(url: String): OutboundObject<SocksOutboundConfigurationObject> {
        val config = decodeProtocol(url)
        val users = if (!config.username.isNullOrEmpty()) {
            listOf(HttpSocksUserObject(user = config.username, pass = config.password ?: ""))
        } else null
        return OutboundObject(
            tag = "proxy",
            protocol = "socks",
            settings = SocksOutboundConfigurationObject(
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
            protocolPrefix = "socks",
            subscriptionId = link.subscriptionId,
            port = config.port,
            address = config.server,
            selected = link.selected,
            remark = config.remark,
            countryISO = countryIsoForServer(config.server),
        )
    }
}

internal object ProxyLinkUtils {

    fun <T> decode(
        url: String,
        factory: (remark: String?, server: String, port: Int, user: String?, pass: String?) -> T,
    ): T {
        val uri = UrlCodec.parseUri(url)
        val host = uri.host ?: throw IllegalArgumentException("Invalid proxy URL: missing host")
        val port = uri.port
        val remark = if (uri.fragment.isNullOrEmpty()) null else percentDecode(uri.fragment)

        var username: String? = null
        var password: String? = null
        val rawUserInfo = uri.userInfo
        if (!rawUserInfo.isNullOrEmpty()) {
            val userInfo = if (rawUserInfo.contains(":")) {
                rawUserInfo
            } else {
                val decoded = tryBase64Decode(rawUserInfo)
                if (decoded.contains(":")) decoded else rawUserInfo
            }
            val idx = userInfo.indexOf(":")
            if (idx >= 0) {
                username = percentDecode(userInfo.substring(0, idx))
                password = percentDecode(userInfo.substring(idx + 1))
            } else {
                username = percentDecode(userInfo)
            }
        }
        return factory(remark, host, port, username, password)
    }

    fun encode(
        scheme: String,
        server: String,
        port: Int,
        username: String?,
        password: String?,
        remark: String?,
    ): String = buildString {
        append(scheme).append("://")
        if (!username.isNullOrEmpty()) {
            append(urlEncode(username))
            if (!password.isNullOrEmpty()) {
                append(":").append(urlEncode(password))
            }
            append("@")
        }
        append(server).append(":").append(port)
        if (!remark.isNullOrEmpty()) {
            append("#").append(urlEncode(remark))
        }
    }

    private fun tryBase64Decode(value: String): String {
        return try {
            Base64Compat.decode(value).decodeToString()
        } catch (e: Exception) {
            value
        }
    }

    private fun percentDecode(s: String?): String {
        if (s == null) return ""
        return try {
            UrlCodec.decode(s)
        } catch (e: Exception) {
            s
        }
    }

    private fun urlEncode(s: String): String = UrlCodec.encode(s)
}
