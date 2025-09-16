package com.android.v2rayForAndroidUI.parser

import com.android.v2rayForAndroidUI.model.Node
import com.android.v2rayForAndroidUI.model.OutboundObject
import com.android.v2rayForAndroidUI.model.ServerObject
import com.android.v2rayForAndroidUI.model.UserObject
import com.android.v2rayForAndroidUI.model.VMESSOutboundConfigurationObject
import com.android.v2rayForAndroidUI.model.protocol.Protocol
import com.android.v2rayForAndroidUI.model.stream.HeaderObject
import com.android.v2rayForAndroidUI.model.stream.HttpHeaderObject
import com.android.v2rayForAndroidUI.model.stream.HttpRequestObject
import com.android.v2rayForAndroidUI.model.stream.HttpResponseObject
import com.android.v2rayForAndroidUI.model.stream.RawSettings
import com.android.v2rayForAndroidUI.model.stream.StreamSettingsObject
import com.android.v2rayForAndroidUI.model.stream.TlsSettings
import com.android.v2rayForAndroidUI.model.stream.WsSettings
import com.google.gson.JsonParser
import java.util.Base64

class VMESSConfigParser: AbstractConfigParser() {

    companion object {
        const val TAG = "VMESSConfigParser"
    }

    override fun parseOutbound(link: String): OutboundObject {

        try {
            // 1. 去掉前缀
            val cleanLink = link.removePrefix("vmess://").trim()

            // 2. Base64 解码
            val decoded = String(Base64.getDecoder().decode(cleanLink))

            // 3. 转成 JSON
            val json = JsonParser.parseString(decoded).asJsonObject
            for ((key, value) in json.entrySet()) {
                //Log.i(TAG,"$key: $value")
                println("$key: $value")
            }
            val uuid = json.get("id").asString
            val tls = json.get("tls").asString
            val host = json.get("host").asString
            val network = json.get("net").asString
            val address = json.get("add").asString
            return OutboundObject(
                protocol = "vmess",
                settings = VMESSOutboundConfigurationObject(
                    vnext = listOf(
                        ServerObject(
                            address = address,
                            port = json.get("port").asInt,
                            users = listOf(
                                UserObject(
                                    id = uuid,
                                    level = 8,
                                    security = "auto"
                                )
                            )
                        )
                    )
                ),
                streamSettings = StreamSettingsObject(
                    network = network,
                    security = "", //check later
                    rawSettings = if (network == "tcp") RawSettings(
                        header = HttpHeaderObject(
                            request = HttpRequestObject(),
                            type = "http"
                        )
                    ) else null,
                    tlsSettings = if (tls == "tls") TlsSettings(
                        serverName = host?:json.get("add").asString,
                        allowInsecure = false
                    ) else null,
                    wsSettings = if (network == "ws") WsSettings(
                        path = "/${uuid}",
                        headers = mapOf(Pair("host",host?:address))
                    ) else null
                ),
                tag = "proxy"
            )

        }catch (e: Exception){
            throw RuntimeException(e)
        }
    }

    override fun preParse(link: String): Node {
        val cleanLink = link.removePrefix("vmess://").trim()

        val decoded = String(Base64.getDecoder().decode(cleanLink))

        val json = JsonParser.parseString(decoded).asJsonObject

        return Node(
            protocol = Protocol.VMESS,
            address = json.get("add").asString,
            port = json.get("port").asInt,
            remark = "vmess-${json.get("add").asString}-${json.get("port").asString}"
        )
    }

}