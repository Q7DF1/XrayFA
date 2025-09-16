package com.android.v2rayForAndroidUI.parser

import com.android.v2rayForAndroidUI.model.MuxObject
import com.android.v2rayForAndroidUI.model.Node
import com.android.v2rayForAndroidUI.model.OutboundObject
import com.android.v2rayForAndroidUI.model.ServerObject
import com.android.v2rayForAndroidUI.model.UserObject
import com.android.v2rayForAndroidUI.model.VLESSOutboundConfigurationObject
import com.android.v2rayForAndroidUI.model.XrayConfiguration
import com.android.v2rayForAndroidUI.model.protocol.Protocol
import com.android.v2rayForAndroidUI.model.protocol.protocols
import com.android.v2rayForAndroidUI.model.stream.RealitySettings
import com.android.v2rayForAndroidUI.model.stream.RawSettings
import com.android.v2rayForAndroidUI.model.stream.StreamSettingsObject
import com.android.v2rayForAndroidUI.model.stream.TlsSettings
import com.android.v2rayForAndroidUI.model.stream.WsSettings
import com.google.gson.Gson

class VLESSConfigParser: AbstractConfigParser(){

    companion object{
        const val TAG = "VLESSConfigParser"
    }


    override fun parseOutbound(link: String): OutboundObject {
        // 1. 去掉协议前缀
        val withoutProtocol = link.removePrefix("vless://")

        // 2. 分离 remark（# 后面的内容）
        val (mainPart, remark) = withoutProtocol.split("#").let {
            it[0] to if (it.size > 1) it[1] else ""
        }

        // 3. 分离 query 参数（? 后面的内容）
        val (userAndServer, query) = mainPart.split("?").let {
            it[0] to if (it.size > 1) it[1] else ""
        }

        // 4. 分离 UUID、服务器、端口
        val (uuid, serverAndPort) = userAndServer.split("@")
        val (server, portStr) = serverAndPort.split(":")
        val port = portStr.toIntOrNull() ?: 0

        // 5. 解析 query 参数为 map
        val queryParams = query.split("&").mapNotNull {
            val kv = it.split("=")
            if (kv.size == 2) kv[0] to kv[1] else null
        }.toMap()
        val network = queryParams["type"] ?: "raw"
        val security = queryParams["security"] ?: "none"
        return OutboundObject(
            protocol = "vless",
            settings = VLESSOutboundConfigurationObject(
                vnext = listOf(
                    ServerObject(
                        address = server,
                        port = port,
                        users = listOf(
                            UserObject(
                                id = uuid,
                                encryption = queryParams["encryption"] ?: "",
                                flow = queryParams["flow"]?:"",
                                level = 0,
                                security = "auto"
                            )
                        )
                    )
                )
            ),
            streamSettings = StreamSettingsObject(
                network = network,
                security = security,
                realitySettings =
                    if (security == "reality") {
                        RealitySettings(
                            fingerprint = queryParams["fp"]?:"",
                            publicKey = queryParams["pbk"]?:"",
                            serverName = queryParams["sni"]?:"",
                            spiderX = "",
                            show = false,
                        )
                    } else {
                        null
                    },
                rawSettings = if (network == "raw") { RawSettings() } else null,
                wsSettings = if (network == "ws") {
                    WsSettings(
                        path = "/${uuid}",
                        headers = mapOf(Pair("host",queryParams["host"]?:""))
                    )
                } else null,
                tlsSettings = if (security == "tls") {
                    TlsSettings(
                        serverName = queryParams["host"]?:""
                    )
                } else null
            ),
            mux = MuxObject(
                concurrency = -1,
                enable = false,
                xudpConcurrency = 8,
                xudpProxyUDP443 = ""
            ),
            tag = "proxy"
        )
    }

    override fun preParse(link: String): Node {
        val withoutProtocol = link.removePrefix("vless://")

        val (mainPart, remark) = withoutProtocol.split("#").let {
            it[0] to if (it.size > 1) it[1] else ""
        }

        val (userAndServer, query) = mainPart.split("?").let {
            it[0] to if (it.size > 1) it[1] else ""
        }

        val (uuid, serverAndPort) = userAndServer.split("@")
        val (server, portStr) = serverAndPort.split(":")
        val port = portStr.toIntOrNull() ?: 0
        return Node(
            protocol = Protocol.VLESS,
            address = server,
            port = port,
            remark = remark
        )
    }


}