package com.android.xrayfa.parser

import com.android.xrayfa.common.core.GeoIpProvider
import com.android.xrayfa.common.repository.ConfigParserSettingsProvider
import com.android.xrayfa.config.XrayConfigEncoder
import com.android.xrayfa.dto.ParseLinkInput
import com.android.xrayfa.dto.ParsedNode
import com.android.xrayfa.dto.ShadowSocksConfig
import com.android.xrayfa.model.OutboundObject
import com.android.xrayfa.model.ShadowSocksOutboundConfigurationObject
import com.android.xrayfa.model.ShadowSocksServerObject
import com.android.xrayfa.model.stream.StreamSettingsObject
import com.android.xrayfa.common.utils.Base64Compat
import com.android.xrayfa.common.utils.UrlCodec

class ShadowSocksConfigParser(
    override val settingsProvider: ConfigParserSettingsProvider,
    override val geoIpProvider: GeoIpProvider,
    override val configEncoder: XrayConfigEncoder,
) : AbstractConfigParser<ShadowSocksOutboundConfigurationObject, ShadowSocksConfig>() {
    override fun decodeProtocol(url: String): ShadowSocksConfig {
        require(url.startsWith("ss://")) { "Not a valid Shadowsocks URL" }
        val content = url.removePrefix("ss://")

        val parts = content.split("#", limit = 2)
        var mainPart = parts[0]
        val tag = if (parts.size > 1) UrlCodec.decode(parts[1]) else null

        val queryParts = mainPart.split("?", limit = 2)
        mainPart = queryParts[0]
        mainPart = mainPart.trimEnd('/')

        val (base64Part, serverPart) = if (mainPart.contains("@")) {
            val lastAtIndex = mainPart.lastIndexOf("@")
            mainPart.substring(0, lastAtIndex) to mainPart.substring(lastAtIndex + 1)
        } else {
            val decodedMain = Base64Compat.decode(mainPart).decodeToString()
            val atIndex = decodedMain.lastIndexOf("@")
            if (atIndex != -1) {
                val userInfo = decodedMain.substring(0, atIndex)
                val serverInfo = decodedMain.substring(atIndex + 1)
                Base64Compat.encode(userInfo.encodeToByteArray()) to serverInfo
            } else {
                throw IllegalArgumentException("Invalid SS URL")
            }
        }

        val decodedUserInfo = Base64Compat.decode(base64Part).decodeToString()
        val userParts = decodedUserInfo.split(":", limit = 2)
        val method = userParts[0]
        val password = if (userParts.size > 1) userParts[1] else ""

        val serverParts = serverPart.split(":", limit = 2)
        val server = serverParts[0]
        val portStr = if (serverParts.size > 1) serverParts[1] else "8388"

        return ShadowSocksConfig(
            method = method,
            password = password,
            server = server,
            port = portStr.toInt(),
            tag = tag,
        )
    }

    override fun encodeProtocol(protocol: ShadowSocksConfig): String {
        val userInfo = "${protocol.method}:${protocol.password}"
        val base64UserInfo = Base64Compat.encode(userInfo.encodeToByteArray())
        val mainPart = "$base64UserInfo@${protocol.server}:${protocol.port}"
        val tagPart = if (!protocol.tag.isNullOrEmpty()) "#${UrlCodec.encode(protocol.tag)}" else ""
        return "ss://$mainPart$tagPart"
    }

    override fun parseOutbound(url: String): OutboundObject<ShadowSocksOutboundConfigurationObject> {
        val shadowSocksConfig = decodeProtocol(url)
        return OutboundObject(
            tag = "proxy",
            protocol = "shadowsocks",
            settings = ShadowSocksOutboundConfigurationObject(
                servers = listOf(
                    ShadowSocksServerObject(
                        address = shadowSocksConfig.server,
                        method = shadowSocksConfig.method,
                        password = shadowSocksConfig.password,
                        port = shadowSocksConfig.port,
                    ),
                ),
            ),
            streamSettings = StreamSettingsObject(
                network = "tcp",
            ),
        )
    }

    override suspend fun preParse(link: ParseLinkInput): ParsedNode {
        val shadowSocksConfig = decodeProtocol(link.content)
        return ParsedNode(
            id = link.id,
            url = link.content,
            protocolPrefix = "ss",
            subscriptionId = link.subscriptionId,
            port = shadowSocksConfig.port,
            address = shadowSocksConfig.server,
            selected = link.selected,
            remark = shadowSocksConfig.tag,
            countryISO = countryIsoForServer(shadowSocksConfig.server),
        )
    }
}
