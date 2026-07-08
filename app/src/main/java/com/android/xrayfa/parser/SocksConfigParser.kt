package com.android.xrayfa.parser

import com.android.xrayfa.XrayAppCompatFactory
import com.android.xrayfa.common.GEO_LITE
import com.android.xrayfa.common.repository.SettingsRepository
import com.android.xrayfa.dto.Link
import com.android.xrayfa.dto.Node
import com.android.xrayfa.dto.SocksConfig
import com.android.xrayfa.model.HttpSocksServerObject
import com.android.xrayfa.model.HttpSocksUserObject
import com.android.xrayfa.model.OutboundObject
import com.android.xrayfa.model.SocksOutboundConfigurationObject
import com.android.xrayfa.model.stream.StreamSettingsObject
import com.android.xrayfa.utils.Device
import com.google.gson.Gson
import kotlinx.coroutines.flow.first
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parser for SOCKS proxy outbounds.
 *
 * Supported share link formats (aligned with the common v2rayN convention):
 *  - socks://host:port#remark                        (no authentication)
 *  - socks://user:pass@host:port#remark              (plain, percent-encoded credentials)
 *  - socks://base64(user:pass)@host:port#remark      (base64 encoded credentials)
 *
 * See https://xtls.github.io/config/outbounds/socks.html
 */
@Singleton
class SocksConfigParser
@Inject constructor(
    override val settingsRepo: SettingsRepository,
    override val gson: Gson
) : AbstractConfigParser<SocksOutboundConfigurationObject, SocksConfig>() {

    override fun decodeProtocol(url: String): SocksConfig {
        require(url.startsWith("socks://") || url.startsWith("socks5://")) { "Not a valid SOCKS URL" }
        return ProxyLinkUtils.decode(url) { remark, server, port, user, pass ->
            SocksConfig(
                remark = remark,
                server = server,
                port = if (port == -1) 1080 else port,
                username = user,
                password = pass
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
            remark = protocol.remark
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
            protocolPrefix = "socks",
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

/**
 * Shared decode/encode helpers for the SOCKS and HTTP proxy share links, which use an
 * identical `scheme://[credentials@]host:port#remark` structure.
 */
internal object ProxyLinkUtils {

    fun <T> decode(
        url: String,
        factory: (remark: String?, server: String, port: Int, user: String?, pass: String?) -> T
    ): T {
        val uri = URI(url)
        val host = uri.host ?: throw IllegalArgumentException("Invalid proxy URL: missing host")
        val port = uri.port
        val remark = if (uri.fragment.isNullOrEmpty()) null else percentDecode(uri.fragment)

        var username: String? = null
        var password: String? = null
        val rawUserInfo = uri.userInfo
        if (!rawUserInfo.isNullOrEmpty()) {
            // Plain "user:pass" is used as-is. Otherwise try base64 (v2rayN style), but only accept
            // the decoded value when it actually looks like "user:pass"; this avoids garbling a plain
            // username that has no password (e.g. socks://user@host) which is also valid base64.
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
        remark: String?
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
            String(Base64.getDecoder().decode(value))
        } catch (e: Exception) {
            value
        }
    }

    private fun percentDecode(s: String?): String {
        if (s == null) return ""
        return try {
            URLDecoder.decode(s, StandardCharsets.UTF_8.name())
        } catch (e: Exception) {
            s
        }
    }

    private fun urlEncode(s: String): String {
        return URLEncoder.encode(s, StandardCharsets.UTF_8.name())
    }
}
