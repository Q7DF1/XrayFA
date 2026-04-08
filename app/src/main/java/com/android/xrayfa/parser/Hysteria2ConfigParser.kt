package com.android.xrayfa.parser

import com.android.xrayfa.XrayAppCompatFactory
import com.android.xrayfa.common.GEO_LITE
import com.android.xrayfa.common.repository.SettingsRepository
import com.android.xrayfa.dto.Hysteria2Config
import com.android.xrayfa.dto.Link
import com.android.xrayfa.dto.Node
import com.android.xrayfa.model.Hysteria2OutboundConfigurationObject
import com.android.xrayfa.model.OutboundObject
import com.android.xrayfa.model.Sockopt
import com.android.xrayfa.model.stream.FinalMask
import com.android.xrayfa.model.stream.HysteriaSettings
import com.android.xrayfa.model.stream.StreamSettingsObject
import com.android.xrayfa.model.stream.TlsSettings
import com.android.xrayfa.utils.Device
import kotlinx.coroutines.flow.first
import java.net.URLDecoder
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Hysteria2ConfigParser @Inject constructor(override val settingsRepo: SettingsRepository)
    : AbstractConfigParser<Hysteria2OutboundConfigurationObject, Hysteria2Config>() {

    override fun decodeProtocol(url: String): Hysteria2Config {
        val decode = URLDecoder.decode(url, "UTF-8")
        val withoutProtocol = decode.removePrefix("hysteria2://")
        val (mainPart, remark) = withoutProtocol.split("#").let {
            it[0] to if (it.size > 1) it[1] else ""
        }
        val (userAndServer, query) = mainPart.split("?").let {
            it[0] to if (it.size > 1) it[1] else ""
        }
        val (uuid, serverAndPort) = userAndServer.split("@")
        val (server, portStr) = serverAndPort.split(":")
        val port = portStr.toIntOrNull() ?: 0
        val queryParams = query.split("&").mapNotNull {
            val kv = it.split("=")
            if (kv.size == 2) kv[0] to kv[1] else null
        }.toMap()
        return Hysteria2Config(
            remark = remark,
            address = server,
            port = port,
            auth = uuid,
            param = queryParams
        )

    }

    override fun encodeProtocol(protocol: Hysteria2Config): String {
        val mainPart = "${protocol.auth}@${protocol.address}:${protocol.port}"
        val query = protocol.param.entries.joinToString("&") { "${it.key}=${it.value}" }
        val remarkEncoded = protocol.remark?.let { URLEncoder.encode(it, "UTF-8") } ?: ""
        return buildString {
            append("hysteria2://")
            append(mainPart)
            if (query.isNotEmpty()) {
                append("?")
                append(query)
            }
            if (remarkEncoded.isNotEmpty()) {
                append("#")
                append(remarkEncoded)
            }
        }
    }


    override fun parseOutbound(url: String): OutboundObject<Hysteria2OutboundConfigurationObject> {
        val hysteria2Config = decodeProtocol(url)
        val alpn = hysteria2Config.param["alpn"]
        return OutboundObject(
            protocol = "hysteria",
            settings = Hysteria2OutboundConfigurationObject(
                address = hysteria2Config.address,
                port = hysteria2Config.port,
                version = hysteria2Config.version
            ),
            streamSettings = StreamSettingsObject(
                network = "hysteria",
                security = "tls",
                sockopt = Sockopt(),
                tlsSettings = TlsSettings(
                    allowInsecure = hysteria2Config.param["allowInsecure"] == "1",
                    alpn = if (alpn != null )listOf(alpn) else null,
                    serverName = hysteria2Config.address
                ),
                hysteriaSettings = HysteriaSettings(
                    auth = hysteria2Config.auth,
                    version = hysteria2Config.version
                ),
                finalMask = FinalMask() // todo
            ),
            tag = "proxy",
        )
    }

    override suspend fun preParse(link: Link): Node {
        val h2Config = decodeProtocol(link.content)
        return Node(
            id = link.id,
            url = link.content,
            protocolPrefix = link.protocolPrefix,
            subscriptionId = link.subscriptionId,
            address = h2Config.address,
            port = h2Config.port,
            selected = link.selected,
            remark = h2Config.remark,
            countryISO = if (settingsRepo.settingsFlow.first().geoLiteInstall) {
                Device.getCountryISOFromIp(
                    geoPath = "${XrayAppCompatFactory.xrayPATH}/$GEO_LITE",
                    ip = h2Config.address
                )
            } else ""
        )
    }
}