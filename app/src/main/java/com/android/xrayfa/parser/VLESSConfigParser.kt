package com.android.xrayfa.parser

import android.os.Build
import androidx.annotation.RequiresApi
import com.android.xrayfa.XrayAppCompatFactory
import com.android.xrayfa.common.repository.SettingsRepository
import com.android.xrayfa.dto.Link
import com.android.xrayfa.model.MuxObject
import com.android.xrayfa.dto.Node
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
import com.android.xrayfa.utils.ColorMap
import com.android.xrayfa.utils.Device
import java.net.URLDecoder
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class VLESSConfigParser
@Inject constructor(
    override val settingsRepo: SettingsRepository
): AbstractConfigParser<VLESSOutboundConfigurationObject>(){

    companion object {
        const val TAG = "VLESSConfigParser"
    }

    data class VLESSConfig(
        val protocol: Protocol = Protocol.VLESS,
        val remark: String? = null,
        val uuid: String,
        val server: String,
        val port: Int,
        val param: Map<String,String>
    )


    fun parseVLESS(url: String): VLESSConfig {
        val decode = URLDecoder.decode(url, "UTF-8")
        val withoutProtocol = decode.removePrefix("vless://")

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



        return VLESSConfig(
            remark = remark,
            uuid = uuid,
            server = server,
            port = port,
            param = queryParams
        )

    }
    override fun parseOutbound(url: String): OutboundObject<VLESSOutboundConfigurationObject> {


        val parseVLESS = parseVLESS(url)
        val queryParams = parseVLESS.param
        val network = queryParams["type"] ?: "raw"
        val security = queryParams["security"] ?: "none"
        return OutboundObject(
            protocol = "vless",
            settings = VLESSOutboundConfigurationObject(
                vnext = listOf(
                    ServerObject(
                        address = parseVLESS.server,
                        port = parseVLESS.port,
                        users = listOf(
                            UserObject(
                                id = parseVLESS.uuid,
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
                        path = "${queryParams["path"]}",
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
        val vlessConfig = parseVLESS(link.content)
        return Node(
            id = link.id,
            url = link.content,
            protocolPrefix = link.protocolPrefix,
            subscriptionId = link.subscriptionId,
            address = vlessConfig.server,
            port = vlessConfig.port,
            selected = link.selected,
            remark = vlessConfig.remark,
            countryISO = Device.getCountryISOFromIp(
                geoPath = "${XrayAppCompatFactory.xrayPATH}/GeoLite2-Country.mmdb",
                ip = vlessConfig.server
            )

        )
    }


}