package com.android.xrayfa.parser

import com.android.xrayfa.XrayAppCompatFactory
import com.android.xrayfa.common.GEO_LITE
import com.android.xrayfa.common.repository.SettingsRepository
import com.android.xrayfa.dto.Link
import com.android.xrayfa.dto.Node
import com.android.xrayfa.dto.ShadowSocksConfig
import com.android.xrayfa.model.OutboundObject
import com.android.xrayfa.model.ShadowSocksOutboundConfigurationObject
import com.android.xrayfa.model.ShadowSocksServerObject
import com.android.xrayfa.model.stream.StreamSettingsObject
import com.android.xrayfa.utils.Device
import com.google.gson.Gson
import kotlinx.coroutines.flow.first
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShadowSocksConfigParser
@Inject constructor(
    override val settingsRepo: SettingsRepository,
    override val gson: Gson
): AbstractConfigParser<ShadowSocksOutboundConfigurationObject, ShadowSocksConfig>() {
    override fun decodeProtocol(url: String): ShadowSocksConfig {
        require(url.startsWith("ss://")) { "Not a valid Shadowsocks URL" }
        val content = url.removePrefix("ss://")

        // 1. Split the fragment (tag)
        val parts = content.split("#", limit = 2)
        var mainPart = parts[0]
        val tag = if (parts.size > 1) java.net.URLDecoder.decode(parts[1], "UTF-8") else null

        // 2. Remove query parameters (e.g., ?plugin=...) to prevent port parsing errors
        val queryParts = mainPart.split("?", limit = 2)
        mainPart = queryParts[0]

        // 3. Remove trailing slash if present (handles cases like server:port/)
        mainPart = mainPart.trimEnd('/')

        val (base64Part, serverPart) = if (mainPart.contains("@")) {
            val lastAtIndex = mainPart.lastIndexOf("@")
            mainPart.substring(0, lastAtIndex) to mainPart.substring(lastAtIndex + 1)
        } else {
            // Handle ss://base64(method:password@server:port)
            val decodedMain = String(Base64.getDecoder().decode(mainPart))
            val atIndex = decodedMain.lastIndexOf("@")
            if (atIndex != -1) {
                val userInfo = decodedMain.substring(0, atIndex)
                val serverInfo = decodedMain.substring(atIndex + 1)
                Base64.getEncoder().encodeToString(userInfo.toByteArray()) to serverInfo
            } else {
                throw IllegalArgumentException("Invalid SS URL")
            }
        }

        val decodedUserInfo = String(Base64.getDecoder().decode(base64Part))
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
            port = portStr.toInt(), // The port is now guaranteed to be pure digits
            tag = tag
        )
    }

    override fun encodeProtocol(protocol: ShadowSocksConfig): String {
        val userInfo = "${protocol.method}:${protocol.password}"
        val base64UserInfo = Base64.getEncoder().encodeToString(userInfo.toByteArray())
        val mainPart = "$base64UserInfo@${protocol.server}:${protocol.port}"
        val tagPart = if (!protocol.tag.isNullOrEmpty()) "#${java.net.URLEncoder.encode(protocol.tag, "UTF-8")}" else ""
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
                        port = shadowSocksConfig.port
                    )
                )
            ),
            streamSettings = StreamSettingsObject(
                network = "tcp"
            )
        )
    }

    override suspend fun preParse(link: Link): Node {
        val shadowSocksConfig = decodeProtocol(link.content)
        return Node(
            id = link.id,
            url = link.content,
            protocolPrefix = "ss",
            subscriptionId = link.subscriptionId,
            port = shadowSocksConfig.port,
            address = shadowSocksConfig.server,
            selected = link.selected,
            remark = shadowSocksConfig.tag,
            countryISO = if (settingsRepo.settingsFlow.first().geoLiteInstall) {
                Device.getCountryISOFromIp(
                    geoPath = "${XrayAppCompatFactory.xrayPATH}/$GEO_LITE",
                    ip = shadowSocksConfig.server
                )
            } else ""
        )
    }
}
