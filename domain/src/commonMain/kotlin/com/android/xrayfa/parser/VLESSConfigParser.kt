package com.android.xrayfa.parser

import com.android.xrayfa.common.core.GeoIpProvider
import com.android.xrayfa.common.repository.ConfigParserSettingsProvider
import com.android.xrayfa.config.XrayConfigEncoder
import com.android.xrayfa.dto.ParseLinkInput
import com.android.xrayfa.dto.ParsedNode
import com.android.xrayfa.dto.VLESSConfig
import com.android.xrayfa.model.MuxObject
import com.android.xrayfa.model.OutboundObject
import com.android.xrayfa.model.ServerObject
import com.android.xrayfa.model.UserObject
import com.android.xrayfa.model.VLESSOutboundConfigurationObject

class VLESSConfigParser(
    override val settingsProvider: ConfigParserSettingsProvider,
    override val geoIpProvider: GeoIpProvider,
    override val configEncoder: XrayConfigEncoder,
) : AbstractConfigParser<VLESSOutboundConfigurationObject, VLESSConfig>() {
    override fun decodeProtocol(url: String): VLESSConfig {
        val uri = ShareLinkUri.parse(url, setOf("vless"))
        return VLESSConfig(remark = uri.remark, uuid = uri.credential, server = uri.address,
            port = uri.port, param = uri.params)
    }

    override fun encodeProtocol(protocol: VLESSConfig): String = encodeShareLink(
        "vless", protocol.uuid, protocol.server, protocol.port, protocol.param, protocol.remark,
    )

    companion object {
        const val TAG = "VLESSConfigParser"
    }

    override fun parseOutbound(url: String): OutboundObject<VLESSOutboundConfigurationObject> {
        val parseVLESS = decodeProtocol(url)
        val queryParams = parseVLESS.param
        return OutboundObject(
            protocol = "vless",
            settings = VLESSOutboundConfigurationObject(
                vnext = listOf(
                    ServerObject(
                        address = parseVLESS.server,
                        port = parseVLESS.port,
                        users = listOf(
                            UserObject(
                                id = parseVLESS.uuid,
                                encryption = queryParams.nonBlank("encryption") ?: "none",
                                flow = queryParams["flow"] ?: "",
                                level = 0,
                                security = "auto",
                            ),
                        ),
                    ),
                ),
            ),
            streamSettings = shareLinkStreamSettings(queryParams, parseVLESS.server),
            mux = MuxObject(concurrency = -1, enabled = false, xudpConcurrency = 8, xudpProxyUDP443 = ""),
            tag = "proxy",
        )
    }

    override suspend fun preParse(link: ParseLinkInput): ParsedNode {
        parseOutbound(link.content)
        val vlessConfig = decodeProtocol(link.content)
        return ParsedNode(
            id = link.id,
            url = link.content,
            protocolPrefix = link.protocolPrefix,
            subscriptionId = link.subscriptionId,
            address = vlessConfig.server,
            port = vlessConfig.port,
            selected = link.selected,
            remark = vlessConfig.remark,
            countryISO = countryIsoForServer(vlessConfig.server),
        )
    }
}
