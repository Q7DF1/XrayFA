package com.android.xrayfa.parser

import com.android.xrayfa.common.repository.SettingsRepository
import com.android.xrayfa.dto.Link
import com.android.xrayfa.model.Node
import com.android.xrayfa.model.OutboundObject
import com.android.xrayfa.model.ServerObject
import com.android.xrayfa.model.UserObject
import com.android.xrayfa.model.VMESSOutboundConfigurationObject
import com.android.xrayfa.model.protocol.Protocol
import com.android.xrayfa.model.stream.GrpcSettings
import com.android.xrayfa.model.stream.HttpHeaderObject
import com.android.xrayfa.model.stream.HttpRequestObject
import com.android.xrayfa.model.stream.KcpHeaderObject
import com.android.xrayfa.model.stream.KcpSettings
import com.android.xrayfa.model.stream.RawSettings
import com.android.xrayfa.model.stream.StreamSettingsObject
import com.android.xrayfa.model.stream.TlsSettings
import com.android.xrayfa.model.stream.WsSettings
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VMESSConfigParser
@Inject constructor(
    override val settingsRepo: SettingsRepository
): AbstractConfigParser<VMESSOutboundConfigurationObject>() {

    companion object {
        const val TAG = "VMESSConfigParser"
    }

    data class VMESSConfig(
        val protocol: Protocol = Protocol.VMESS,
        val uuid:String,
        val tls:String,
        val host:String,
        val network:String,
        val address:String,
    )

    override fun parseOutbound(link: String): OutboundObject<VMESSOutboundConfigurationObject> {

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
                                    security = json.get("scy")?.asString?:"auto"
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
                        ),
                    ) else null,
                    kcpSettings = if (network == "kcp") KcpSettings(
                        header = KcpHeaderObject(
                            type = json.get("type").asString
                                ?:throw IllegalArgumentException("no type"),
                        ),
                        seed = json.get("path").asString
                    ) else null,
                    tlsSettings = if (tls == "tls") TlsSettings(
                        serverName = host?:json.get("add").asString,
                        allowInsecure = false
                    ) else null,
                    grpcSettings = if (network == "grpc") GrpcSettings(
                        serviceName = json.get("path").asString
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

    override fun preParse(link: Link): Node {
        val cleanLink = link.content.removePrefix("vmess://").trim()

        val decoded = String(Base64.getDecoder().decode(cleanLink))

        val json = JsonParser.parseString(decoded).asJsonObject

        return Node(
            id = link.id,
            protocol = Protocol.VMESS,
            address = json.get("add").asString,
            port = json.get("port").asInt,
            selected = link.selected,
            remark = json.get("ps").asString
                ?:"vmess-${json.get("add").asString}-${json.get("port").asString}"
        )
    }

}