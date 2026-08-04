package com.android.xrayfa.parser

import com.android.xrayfa.common.core.GeoIpProvider
import com.android.xrayfa.common.repository.ConfigParserSettingsProvider
import com.android.xrayfa.config.XrayConfigEncoder
import com.android.xrayfa.dto.ParseLinkInput
import com.android.xrayfa.dto.ParsedNode
import com.android.xrayfa.dto.VMESSConfig
import com.android.xrayfa.model.OutboundObject
import com.android.xrayfa.model.ServerObject
import com.android.xrayfa.model.UserObject
import com.android.xrayfa.model.VMESSOutboundConfigurationObject
import com.android.xrayfa.model.stream.GrpcSettings
import com.android.xrayfa.model.stream.HttpHeaderObject
import com.android.xrayfa.model.stream.HttpRequestObject
import com.android.xrayfa.model.stream.KcpHeaderObject
import com.android.xrayfa.model.stream.KcpSettings
import com.android.xrayfa.model.stream.RawSettings
import com.android.xrayfa.model.stream.StreamSettingsObject
import com.android.xrayfa.model.stream.TlsSettings
import com.android.xrayfa.model.stream.WsSettings
import com.android.xrayfa.common.utils.Base64Compat
import kotlinx.serialization.json.JsonPrimitive

class VMESSConfigParser(
    override val settingsProvider: ConfigParserSettingsProvider,
    override val geoIpProvider: GeoIpProvider,
    override val configEncoder: XrayConfigEncoder,
) : AbstractConfigParser<VMESSOutboundConfigurationObject, VMESSConfig>() {
    override fun decodeProtocol(url: String): VMESSConfig {
        val cleanLink = url.removePrefix("vmess://").trim()
        val decoded = Base64Compat.decode(cleanLink).decodeToString()
        val json = decodeVmessJson(decoded)
        val uuid = json.stringOrEmpty("id")
        val tls = json.optionalString("tls") ?: ""
        val host = json.optionalString("host") ?: ""
        val network = json.optionalString("net") ?: "tcp"
        val address = json.stringOrEmpty("add")
        return VMESSConfig(
            uuid = uuid,
            tls = tls,
            host = host,
            network = network,
            address = address,
            others = json,
        )
    }

    override fun encodeProtocol(protocol: VMESSConfig): String {
        val json = protocol.others.copyWithUpdates(
            mapOf(
                "v" to JsonPrimitive("2"),
                "id" to JsonPrimitive(protocol.uuid),
                "tls" to JsonPrimitive(protocol.tls),
                "host" to JsonPrimitive(protocol.host),
                "net" to JsonPrimitive(protocol.network),
                "add" to JsonPrimitive(protocol.address),
            ),
        )
        val jsonString = encodeVmessJson(json)
        val encoded = Base64Compat.encode(jsonString.encodeToByteArray())
        return "vmess://$encoded"
    }

    companion object {
        const val TAG = "VMESSConfigParser"
    }

    override fun parseOutbound(url: String): OutboundObject<VMESSOutboundConfigurationObject> {
        try {
            val vmess = decodeProtocol(url)
            val uuid = vmess.uuid
            val tls = vmess.tls
            val host = vmess.host
            val network = vmess.network
            val address = vmess.address
            val json = vmess.others
            return OutboundObject(
                protocol = "vmess",
                settings = VMESSOutboundConfigurationObject(
                    vnext = listOf(
                        ServerObject(
                            address = address,
                            port = json.intValue("port"),
                            users = listOf(
                                UserObject(
                                    id = uuid,
                                    level = 8,
                                    security = json.optionalString("scy") ?: "auto",
                                ),
                            ),
                        ),
                    ),
                ),
                streamSettings = StreamSettingsObject(
                    network = network,
                    security = if (tls == "tls") "tls" else "",
                    rawSettings = if (network == "tcp") {
                        RawSettings(
                            header = HttpHeaderObject(
                                request = HttpRequestObject(),
                                type = "http",
                            ),
                        )
                    } else null,
                    kcpSettings = if (network == "kcp") {
                        KcpSettings(
                            header = KcpHeaderObject(
                                type = json.optionalString("type") ?: "none",
                            ),
                            seed = json.optionalString("path") ?: "",
                        )
                    } else null,
                    tlsSettings = if (tls == "tls") {
                        TlsSettings(
                            serverName = if (host.isNotEmpty()) host else address,
                            allowInsecure = json.optionalString("allowInsecure") == "1",
                        )
                    } else null,
                    grpcSettings = if (network == "grpc") {
                        GrpcSettings(
                            serviceName = json.optionalString("path") ?: "",
                        )
                    } else null,
                    wsSettings = if (network == "ws") {
                        WsSettings(
                            path = json.optionalString("path") ?: "/$uuid",
                            headers = mapOf(Pair("host", if (host.isNotEmpty()) host else address)),
                        )
                    } else null,
                ),
                tag = "proxy",
            )
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    override suspend fun preParse(link: ParseLinkInput): ParsedNode {
        val vmess = decodeProtocol(link.content)
        val json = vmess.others
        return ParsedNode(
            id = link.id,
            url = link.content,
            protocolPrefix = link.protocolPrefix,
            subscriptionId = link.subscriptionId,
            address = json.stringOrEmpty("add"),
            port = json.intValue("port"),
            selected = link.selected,
            remark = json.optionalString("ps")
                ?: "vmess-${json.stringOrEmpty("add")}-${json.intValue("port")}",
            countryISO = countryIsoForServer(json.stringOrEmpty("add")),
        )
    }
}
