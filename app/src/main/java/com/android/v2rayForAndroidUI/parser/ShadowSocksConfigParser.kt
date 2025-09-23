package com.android.v2rayForAndroidUI.parser

import com.android.v2rayForAndroidUI.model.Link
import com.android.v2rayForAndroidUI.model.Node
import com.android.v2rayForAndroidUI.model.OutboundObject
import com.android.v2rayForAndroidUI.model.ShadowSocksOutboundConfigurationObject
import com.android.v2rayForAndroidUI.model.ShadowSocksServerObject
import com.android.v2rayForAndroidUI.model.protocol.Protocol
import com.android.v2rayForAndroidUI.model.stream.StreamSettingsObject
import java.util.Base64

class ShadowSocksConfigParser: AbstractConfigParser() {

    data class ShadowSocksConfig(
        val method: String,
        val password: String,
        val server: String,
        val port: Int,
        val tag: String?
    )

    fun parseLink(url: String): ShadowSocksConfig {
        require(url.startsWith("ss://")) { "Not a valid Shadowsocks URL" }


        val content = url.removePrefix("ss://")

        val parts = content.split("#", limit = 2)
        val mainPart = parts[0]
        val tag = if (parts.size > 1) parts[1] else null

        val (base64Part, serverPart) = mainPart.split("@", limit = 2)

        val decoded = String(Base64.getDecoder().decode(base64Part))
        val (method, password) = decoded.split(":", limit = 2)

        val (server, portStr) = serverPart.split(":", limit = 2)

        return ShadowSocksConfig(
            method = method,
            password = password,
            server = server,
            port = portStr.toInt(),
            tag = tag
        )
    }

    override fun parseOutbound(url: String): OutboundObject {
        val shadowSocksConfig = parseLink(url)
        return OutboundObject(
            tag = "proxy",
            protocol = "shadowsocks",
            settings = ShadowSocksOutboundConfigurationObject(
                services = listOf(
                    ShadowSocksServerObject(
                        address = shadowSocksConfig.server,
                        method = shadowSocksConfig.method,
                        password = shadowSocksConfig.password,
                        port = shadowSocksConfig.port
                    )
                )
            ),
            streamSettings = StreamSettingsObject(
                network = "tcp"
            )
        )
    }

    override fun preParse(link: Link): Node {
        val shadowSocksConfig = parseLink(link.content)
        return Node(
            id = link.id,
            protocol = Protocol.SHADOW_SOCKS,
            port = shadowSocksConfig.port,
            address = shadowSocksConfig.server,
            remark = shadowSocksConfig.tag
        )
    }

}