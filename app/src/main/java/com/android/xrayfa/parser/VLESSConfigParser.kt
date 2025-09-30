package com.android.xrayfa.parser

import com.android.xrayfa.model.AbsOutboundConfigurationObject
import com.android.xrayfa.model.Link
import com.android.xrayfa.model.MuxObject
import com.android.xrayfa.model.Node
import com.android.xrayfa.model.OutboundObject
import com.android.xrayfa.model.ServerObject
import com.android.xrayfa.model.UserObject
import com.android.xrayfa.model.VLESSOutboundConfigurationObject
import com.android.xrayfa.model.protocol.Protocol
import com.android.xrayfa.model.stream.GrpcSettings
import com.android.xrayfa.model.stream.RealitySettings
import com.android.xrayfa.model.stream.RawSettings
import com.android.xrayfa.model.stream.StreamSettingsObject
import com.android.xrayfa.model.stream.TlsSettings
import com.android.xrayfa.model.stream.WsSettings

class VLESSConfigParser: AbstractConfigParser<VLESSOutboundConfigurationObject>(){

    companion object{
        const val TAG = "VLESSConfigParser"
    }


    override fun parseOutbound(link: String): OutboundObject<VLESSOutboundConfigurationObject> {
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
                    } else null,
                rawSettings = if (network == "raw") { RawSettings() } else null,
                wsSettings = if (network == "ws") {
                    WsSettings(
                        path = "/${uuid}",
                        headers = mapOf(Pair("host",queryParams["host"]?:""))
                    )
                } else null,
                grpcSettings = if (network == "grpc")GrpcSettings(
                    serviceName = queryParams["serviceName"]?:"",
                    multiMode = false
                ) else null,
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

    override fun preParse(link: Link): Node {
        val withoutProtocol = link.content.removePrefix("vless://")

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
            id = link.id,
            protocol = Protocol.VLESS,
            address = server,
            port = port,
            selected = link.selected,
            remark = remark
        )
    }


}