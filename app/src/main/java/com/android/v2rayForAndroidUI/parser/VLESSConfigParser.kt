package com.android.v2rayForAndroidUI.parser

import android.util.Log
import com.android.v2rayForAndroidUI.model.MuxObject
import com.android.v2rayForAndroidUI.model.NoneOutboundConfigurationObject
import com.android.v2rayForAndroidUI.model.OutboundObject
import com.android.v2rayForAndroidUI.model.RoutingObject
import com.android.v2rayForAndroidUI.model.RuleObject
import com.android.v2rayForAndroidUI.model.ServerObject
import com.android.v2rayForAndroidUI.model.UserObject
import com.android.v2rayForAndroidUI.model.VLESSOutboundConfigurationObject
import com.android.v2rayForAndroidUI.model.XrayConfiguration
import com.android.v2rayForAndroidUI.model.stream.RealitySettings
import com.android.v2rayForAndroidUI.model.stream.RawSettings
import com.android.v2rayForAndroidUI.model.stream.StreamSettingsObject
import com.google.gson.Gson
import kotlin.math.exp

class VLESSConfigParser: AbstractConfigParser(){

    companion object{
        const val TAG = "VLESSConfigParser"
    }

    override fun getJsonConfigStringFromLink(link: String): String {

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

        // 6. 给每个字段赋值
        val protocol = "vless"
        val encryption = queryParams["encryption"] ?: ""
        val flow = queryParams["flow"] ?: ""
        val security = queryParams["security"] ?: ""
        val sni = queryParams["sni"] ?: ""
        val fingerprint = queryParams["fp"] ?: ""
        val publicKey = queryParams["pbk"] ?: ""
        val type = queryParams["type"] ?: ""
        val headerType = queryParams["headerType"] ?: ""

        // ---- 测试打印（可选） ----
        println("protocol: $protocol")
        println("uuid: $uuid")
        println("server: $server")
        println("port: $port")
        println("encryption: $encryption")
        println("flow: $flow")
        println("security: $security")
        println("sni: $sni")
        println("fingerprint: $fingerprint")
        println("publicKey: $publicKey")
        println("type: $type")
        println("headerType: $headerType")
        println("remark: $remark")

        val vlessConfig = XrayConfiguration(
            dns = getBaseDnsConfig(),
            log = getBaseLogObject(),
            inbounds = listOf(getBaseInboundConfig()),
            outbounds = listOf(
                getBaseOutboundConfig(),

                OutboundObject(
                    protocol = protocol,
                    settings = VLESSOutboundConfigurationObject(
                        vnext = listOf(
                            ServerObject(
                                address = server,
                                port = port,
                                users = listOf(
                                    UserObject(
                                        id = uuid,
                                        encryption = encryption,
                                        flow = flow,
                                        level = 0,
                                    )
                                )
                            )
                        )
                    ),
                    streamSettings = StreamSettingsObject(
                        security = security,
                        realitySettings = RealitySettings(
                            fingerprint = fingerprint,
                            publicKey = publicKey,
                            serverName = sni,
                            spiderX = "",
                            show = false,
                        ),
                        rawSettings = RawSettings()

                    ),
                    mux = MuxObject(
                        concurrency = -1,
                        enable = false,
                        xudpConcurrency = 8,
                        xudpProxyUDP443 = ""
                    ),
                    tag = "proxy"
                )
            ),
            routing = getBaseRoutingObject()
        )
        val config = Gson().toJson(vlessConfig)
        println(config)

        return config
    }


}