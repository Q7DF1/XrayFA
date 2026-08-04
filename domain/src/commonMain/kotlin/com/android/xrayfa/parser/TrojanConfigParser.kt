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
import com.android.xrayfa.model.stream.GrpcSettings
import com.android.xrayfa.model.stream.StreamSettingsObject
import com.android.xrayfa.model.stream.TlsSettings
import com.android.xrayfa.model.stream.WsSettings
import com.android.xrayfa.common.utils.UrlCodec

class TrojanConfigParser(
    override val settingsProvider: ConfigParserSettingsProvider,
    override val geoIpProvider: GeoIpProvider,
    override val configEncoder: XrayConfigEncoder,
) : AbstractConfigParser<TrojanOutboundConfigurationObject, TrojanConfig>() {
    override fun decodeProtocol(url: String): TrojanConfig {
        val uri = UrlCodec.parseUri(url)
        val scheme = uri.scheme ?: "trojan"
        val password = percentDecode(uri.userInfo ?: "")
        val host = uri.host
        val port = if (uri.port == -1) null else uri.port
        val remark = if (uri.fragment.isNullOrEmpty()) null else percentDecode(uri.fragment)

        val params = mutableMapOf<String, String>()
        uri.query?.split("&")?.forEach { pair ->
            val kv = pair.split("=", limit = 2)
            if (kv.size == 2) {
                params[percentDecode(kv[0])] = percentDecode(kv[1])
            } else if (kv.size == 1) {
                params[percentDecode(kv[0])] = ""
            }
        }

        return TrojanConfig(
            scheme = scheme,
            password = password,
            host = host,
            port = port,
            params = params,
            remark = remark,
            original = url,
        )
    }

    override fun encodeProtocol(protocol: TrojanConfig): String {
        val userInfo = UrlCodec.encode(protocol.password)
        val query = protocol.params.entries.joinToString("&") {
            "${UrlCodec.encode(it.key)}=${UrlCodec.encode(it.value)}"
        }
        val fragment = protocol.remark?.let { "#${UrlCodec.encode(it)}" } ?: ""

        return buildString {
            append("trojan://")
            append(userInfo)
            append("@")
            append(protocol.host)
            append(":")
            append(protocol.port)
            if (query.isNotEmpty()) {
                append("?")
                append(query)
            }
            append(fragment)
        }
    }

    companion object {
        private fun percentDecode(s: String?): String {
            if (s == null) return ""
            return try {
                UrlCodec.decode(s)
            } catch (e: Exception) {
                s
            }
        }
    }

    override fun parseOutbound(url: String): OutboundObject<TrojanOutboundConfigurationObject> {
        val trojanConfig = decodeProtocol(url)
        val network = trojanConfig.params["type"] ?: "tcp"
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
            streamSettings = StreamSettingsObject(
                network = network,
                security = trojanConfig.params["security"] ?: "tls",
                tlsSettings = TlsSettings(
                    serverName = trojanConfig.host,
                    allowInsecure = trojanConfig.params["allowInsecure"] == "1",
                ),
                wsSettings = if (network == "ws") {
                    WsSettings(
                        path = trojanConfig.params["path"] ?: "",
                        headers = mapOf(Pair("Host", trojanConfig.host ?: "")),
                    )
                } else null,
                grpcSettings = if (network == "grpc") {
                    GrpcSettings(
                        serviceName = trojanConfig.params["serviceName"] ?: "",
                    )
                } else null,
            ),
        )
    }

    override suspend fun preParse(link: ParseLinkInput): ParsedNode {
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
