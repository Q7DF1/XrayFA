package com.android.xrayfa.parser

import com.android.xrayfa.common.core.GeoIpProvider
import com.android.xrayfa.common.repository.ConfigParserSettingsProvider
import com.android.xrayfa.config.XrayConfigEncoder
import com.android.xrayfa.dto.ParseLinkInput
import com.android.xrayfa.dto.ParsedNode
import com.android.xrayfa.dto.TrojanConfig
import com.android.xrayfa.model.OutboundObject
import com.android.xrayfa.model.TrojanOutboundConfigurationObject
import com.android.xrayfa.model.TrojanServerObject

class TrojanConfigParser(
    override val settingsProvider: ConfigParserSettingsProvider,
    override val geoIpProvider: GeoIpProvider,
    override val configEncoder: XrayConfigEncoder,
) : AbstractConfigParser<TrojanOutboundConfigurationObject, TrojanConfig>() {
    override fun decodeProtocol(url: String): TrojanConfig {
        val uri = ShareLinkUri.parse(url, setOf("trojan"), defaultPort = 443)
        return TrojanConfig(scheme = "trojan", password = uri.credential, host = uri.address,
            port = uri.port, params = uri.params, remark = uri.remark, original = url)
    }

    override fun encodeProtocol(protocol: TrojanConfig): String = encodeShareLink(
        "trojan", protocol.password, requireNotNull(protocol.host), protocol.port ?: 443,
        protocol.params, protocol.remark,
    )

    override fun parseOutbound(url: String): OutboundObject<TrojanOutboundConfigurationObject> {
        val trojanConfig = decodeProtocol(url)
        return OutboundObject(
            tag = "proxy",
            protocol = "trojan",
            settings = TrojanOutboundConfigurationObject(
                servers = listOf(
                    TrojanServerObject(
                        address = trojanConfig.host,
                        port = trojanConfig.port,
                        password = trojanConfig.password,
                    ),
                ),
            ),
            streamSettings = shareLinkStreamSettings(trojanConfig.params, requireNotNull(trojanConfig.host), "tls"),
        )
    }

    override suspend fun preParse(link: ParseLinkInput): ParsedNode {
        parseOutbound(link.content)
        val trojanConfig = decodeProtocol(link.content)
        return ParsedNode(
            id = link.id,
            url = link.content,
            subscriptionId = link.subscriptionId,
            protocolPrefix = link.protocolPrefix,
            address = trojanConfig.host ?: "unknown",
            port = trojanConfig.port ?: 0,
            remark = trojanConfig.remark,
            countryISO = countryIsoForServer(trojanConfig.host ?: ""),
        )
    }
}
