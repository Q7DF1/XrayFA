package com.android.xrayfa.parser

import com.android.xrayfa.common.core.GeoIpProvider
import com.android.xrayfa.common.repository.ConfigParserSettingsProvider
import com.android.xrayfa.config.XrayConfigEncoder
import com.android.xrayfa.dto.Hysteria2Config
import com.android.xrayfa.dto.ParseLinkInput
import com.android.xrayfa.dto.ParsedNode
import com.android.xrayfa.model.Hysteria2OutboundConfigurationObject
import com.android.xrayfa.model.OutboundObject
import com.android.xrayfa.model.Sockopt
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import com.android.xrayfa.model.stream.MaskObject
import com.android.xrayfa.model.stream.FinalMask
import com.android.xrayfa.model.stream.HysteriaSettings
import com.android.xrayfa.model.stream.StreamSettingsObject

class Hysteria2ConfigParser(
    override val settingsProvider: ConfigParserSettingsProvider,
    override val geoIpProvider: GeoIpProvider,
    override val configEncoder: XrayConfigEncoder,
) : AbstractConfigParser<Hysteria2OutboundConfigurationObject, Hysteria2Config>() {

    override fun decodeProtocol(url: String): Hysteria2Config {
        val uri = ShareLinkUri.parse(url, setOf("hysteria2", "hy2"), defaultPort = 443)
        return Hysteria2Config(remark = uri.remark, address = uri.address, port = uri.port,
            auth = uri.credential, param = uri.params)
    }

    override fun encodeProtocol(protocol: Hysteria2Config): String = encodeShareLink(
        "hysteria2", protocol.auth, protocol.address, protocol.port, protocol.param, protocol.remark,
    )

    override fun parseOutbound(url: String): OutboundObject<Hysteria2OutboundConfigurationObject> {
        val hysteria2Config = decodeProtocol(url)
        val params = hysteria2Config.param
        val obfs = params.nonBlank("obfs")
        require(obfs == null || obfs == "salamander") { "Unsupported Hysteria2 obfs: $obfs" }
        val mask = if (obfs == "salamander") {
            val password = params.nonBlank("obfs-password")
            require(password != null) { "Hysteria2 obfs-password is required" }
            FinalMask(udp = listOf(MaskObject(type = "salamander", settings = buildJsonObject { put("password", password) })))
        } else null
        return OutboundObject(
            protocol = "hysteria",
            settings = Hysteria2OutboundConfigurationObject(
                address = hysteria2Config.address,
                port = hysteria2Config.port,
                version = hysteria2Config.version,
            ),
            streamSettings = StreamSettingsObject(
                network = "hysteria",
                security = "tls",
                sockopt = Sockopt(),
                tlsSettings = shareLinkTls(params, hysteria2Config.address),
                hysteriaSettings = HysteriaSettings(
                    auth = hysteria2Config.auth,
                    version = hysteria2Config.version,
                ),
                finalMask = mask,
            ),
            tag = "proxy",
        )
    }

    override suspend fun preParse(link: ParseLinkInput): ParsedNode {
        parseOutbound(link.content)
        val h2Config = decodeProtocol(link.content)
        return ParsedNode(
            id = link.id,
            url = link.content,
            protocolPrefix = "hysteria2",
            subscriptionId = link.subscriptionId,
            address = h2Config.address,
            port = h2Config.port,
            selected = link.selected,
            remark = h2Config.remark,
            countryISO = countryIsoForServer(h2Config.address),
        )
    }
}
