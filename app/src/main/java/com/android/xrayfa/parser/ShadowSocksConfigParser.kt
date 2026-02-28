package com.android.xrayfa.parser

import com.android.xrayfa.XrayAppCompatFactory
import com.android.xrayfa.common.GEO_LITE
import com.android.xrayfa.common.repository.SettingsRepository
import com.android.xrayfa.dto.Link
import com.android.xrayfa.dto.Node
import com.android.xrayfa.model.OutboundObject
import com.android.xrayfa.model.ShadowSocksOutboundConfigurationObject
import com.android.xrayfa.model.ShadowSocksServerObject
import com.android.xrayfa.model.stream.StreamSettingsObject
import com.android.xrayfa.utils.Device
import kotlinx.coroutines.flow.first
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShadowSocksConfigParser
@Inject constructor(
    override val settingsRepo: SettingsRepository
): AbstractConfigParser<ShadowSocksOutboundConfigurationObject>() {

    data class ShadowSocksConfig(
        val method: String,
        val password: String,
        val server: String,
        val port: Int,
        val tag: String?
    )

    fun decodeShadowSocks(url: String): ShadowSocksConfig {
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

    override fun parseOutbound(url: String): OutboundObject<ShadowSocksOutboundConfigurationObject> {
        val shadowSocksConfig = decodeShadowSocks(url)
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
        val shadowSocksConfig = decodeShadowSocks(link.content)
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