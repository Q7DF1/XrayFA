package com.android.v2rayForAndroidUI.parser

import com.android.v2rayForAndroidUI.model.Link
import com.android.v2rayForAndroidUI.model.Node
import com.android.v2rayForAndroidUI.model.OutboundObject
import com.android.v2rayForAndroidUI.model.TrojanOutboundConfigurationObject
import com.android.v2rayForAndroidUI.model.TrojanServerObject
import com.android.v2rayForAndroidUI.model.protocol.Protocol
import com.android.v2rayForAndroidUI.model.stream.GrpcSettings
import com.android.v2rayForAndroidUI.model.stream.StreamSettingsObject
import com.android.v2rayForAndroidUI.model.stream.TlsSettings
import com.android.v2rayForAndroidUI.model.stream.WsSettings
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class TrojanConfigParser: AbstractConfigParser() {


    data class TrojanConfig(
        val scheme: String,
        val password: String,
        val host: String?,
        val port: Int?,
        val params: Map<String, String>,
        val remark: String?,
        val original: String
    )

    private fun percentDecode(s: String?): String {
        if (s == null) return ""
        return try {
            URLDecoder.decode(s, StandardCharsets.UTF_8.name())
        } catch (e: Exception) {
            s
        }
    }

    private fun parseTrojan(link: String): TrojanConfig {

        val uri = URI(link)

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
            original = link
        )
    }

    override fun parseOutbound(link: String): OutboundObject {
        val trojanConfig = parseTrojan(link)
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

    override fun preParse(link: Link): Node {
        val trojanConfig = parseTrojan(link.content)
        return Node(
            id = link.id,
            protocol = Protocol.TROJAN,
            address = trojanConfig.host?:"unknown",
            port = trojanConfig.port?:0,
            remark = trojanConfig.remark
        )
    }
}