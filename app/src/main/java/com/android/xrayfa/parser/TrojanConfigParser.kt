package com.android.xrayfa.parser

import com.android.xrayfa.XrayAppCompatFactory
import com.android.xrayfa.common.GEO_LITE
import com.android.xrayfa.common.repository.SettingsRepository
import com.android.xrayfa.dto.Link
import com.android.xrayfa.dto.Node
import com.android.xrayfa.dto.TrojanConfig
import com.android.xrayfa.model.OutboundObject
import com.android.xrayfa.model.TrojanOutboundConfigurationObject
import com.android.xrayfa.model.TrojanServerObject
import com.android.xrayfa.model.stream.GrpcSettings
import com.android.xrayfa.model.stream.StreamSettingsObject
import com.android.xrayfa.model.stream.TlsSettings
import com.android.xrayfa.model.stream.WsSettings
import com.android.xrayfa.utils.Device
import kotlinx.coroutines.flow.first
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrojanConfigParser
@Inject constructor(
    override val settingsRepo: SettingsRepository
): AbstractConfigParser<TrojanOutboundConfigurationObject, TrojanConfig>() {
    override fun decodeProtocol(url: String): TrojanConfig {
        val uri = URI(url)
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
            original = url
        )
    }

    override fun encodeProtocol(protocol: TrojanConfig): String {
        val userInfo = URLEncoder.encode(protocol.password, StandardCharsets.UTF_8.name())
        val query = protocol.params.entries.joinToString("&") {
            "${URLEncoder.encode(it.key, StandardCharsets.UTF_8.name())}=${URLEncoder.encode(it.value, StandardCharsets.UTF_8.name())}"
        }
        val fragment = protocol.remark?.let { "#${URLEncoder.encode(it, StandardCharsets.UTF_8.name())}" } ?: ""

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
                URLDecoder.decode(s, StandardCharsets.UTF_8.name())
            } catch (e: Exception) {
                s
            }
        }
    }

    override fun parseOutbound(url: String): OutboundObject<TrojanOutboundConfigurationObject> {
        val trojanConfig = decodeProtocol(url)
        val network = trojanConfig.params.getOrDefault("type", "tcp")
        return OutboundObject(
            tag = "proxy",
            protocol = "trojan",
            settings = TrojanOutboundConfigurationObject(
                servers = listOf(TrojanServerObject(
                    address = trojanConfig.host,
                    port = trojanConfig.port,
                    password =trojanConfig.password
                ))
            ),
            streamSettings = StreamSettingsObject(
                network = network,
                security = trojanConfig.params.getOrDefault("security", "tls"),
                tlsSettings = TlsSettings(serverName = trojanConfig.host, allowInsecure = false),
                wsSettings = if (network == "ws") WsSettings(
                    path = trojanConfig.params.getOrDefault("path",""),
                    headers = mapOf(Pair("Host",trojanConfig.host?:""))
                ) else null,
                grpcSettings = if (network == "grpc") GrpcSettings(
                    serviceName = trojanConfig.params.getOrDefault("serviceName","")
                ) else null
            )
        )
    }

    override suspend fun preParse(link: Link): Node {
        val trojanConfig = decodeProtocol(link.content)
        return Node(
            id = link.id,
            url = link.content,
            subscriptionId = link.subscriptionId,
            protocolPrefix = link.protocolPrefix,
            address = trojanConfig.host?:"unknown",
            port = trojanConfig.port?:0,
            remark = trojanConfig.remark,
            countryISO = if (settingsRepo.settingsFlow.first().geoLiteInstall) {
                Device.getCountryISOFromIp(
                    geoPath = "${XrayAppCompatFactory.xrayPATH}/$GEO_LITE",
                    ip = trojanConfig.host?:""
                )
            } else ""
        )
    }
}
