package com.android.xrayfa.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.android.xrayfa.dto.VLESSConfig
import com.android.xrayfa.dto.VMESSConfig
import com.android.xrayfa.dto.ShadowSocksConfig
import com.android.xrayfa.dto.TrojanConfig
import com.android.xrayfa.model.AbsOutboundConfigurationObject
import com.android.xrayfa.model.OutboundObject
import com.android.xrayfa.model.protocol.Protocol
import com.android.xrayfa.parser.ParserFactory
import com.android.xrayfa.parser.ShadowSocksConfigParser
import com.android.xrayfa.parser.TrojanConfigParser
import com.android.xrayfa.parser.VMESSConfigParser
import com.android.xrayfa.repository.NodeRepository
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

class DetailViewmodel(
    val parserFactory: ParserFactory,
    val nodeRepository: NodeRepository,
): ViewModel() {

    private fun <T: AbsOutboundConfigurationObject> parseProtocol(
        protocol: String,
        content: String
    ): OutboundObject<T> {
        @Suppress("UNCHECKED_CAST")
        return  parserFactory.getParser(protocol).parseOutbound(content) as OutboundObject<T>
    }

    fun parseVLESSProtocol(content: String): VLESSConfig {
        return parserFactory.vlessConfigParser.decodeProtocol(content)
    }

    fun parseVMESSProtocol(content: String): VMESSConfig {
        return parserFactory.vmessConfigParser.decodeProtocol(content)
    }

    fun parseTrojanProtocol(content:String): TrojanConfig {
        return parserFactory.trojanConfigParser.decodeProtocol(content)
    }
    fun parseShadowSocks(content:String): ShadowSocksConfig {
        return parserFactory.shadowSocksConfigParser.decodeProtocol(content)
    }

    fun saveNode(
        protocol: Protocol,
        remarks: String,
        address: String,
        port: Int,
        id: String,
        flow: String = "",
        vlessEncryption: String = "none",
        vmessSecurity: String = "auto",
        ssMethod: String = "aes-256-gcm",
        network: String = "tcp",
        transportSecurity: String = "none",
        wsPath: String = "/",
        wsHost: String = "",
        grpcServiceName: String = "",
        sni: String = "",
        fingerprint: String = "chrome",
        publicKey: String = "",
        shortId: String = ""
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val url = when (protocol) {
                Protocol.VLESS -> {
                    val params = mutableMapOf(
                        "type" to network,
                        "security" to transportSecurity,
                        "encryption" to vlessEncryption,
                        "flow" to flow
                    )
                    if (network == "ws") {
                        params["path"] = wsPath
                        params["host"] = wsHost
                    } else if (network == "grpc") {
                        params["serviceName"] = grpcServiceName
                    }
                    if (transportSecurity == "tls" || transportSecurity == "reality") {
                        params["sni"] = sni
                        params["fp"] = fingerprint
                    }
                    if (transportSecurity == "reality") {
                        params["pbk"] = publicKey
                        params["sid"] = shortId
                    }
                    
                    parserFactory.vlessConfigParser.encodeProtocol(VLESSConfig(
                        remark = remarks,
                        uuid = id,
                        server = address,
                        port = port,
                        param = params
                    ))
                }
                Protocol.VMESS -> {
                    val others = JsonObject().apply {
                        addProperty("v", "2")
                        addProperty("ps", remarks)
                        addProperty("add", address)
                        addProperty("port", port)
                        addProperty("id", id)
                        addProperty("aid", "0")
                        addProperty("scy", vmessSecurity)
                        addProperty("net", network)
                        addProperty("type", "none")
                        addProperty("host", wsHost)
                        addProperty("path", if (network == "ws") wsPath else if (network == "grpc") grpcServiceName else "")
                        addProperty("tls", if (transportSecurity == "none") "" else transportSecurity)
                        addProperty("sni", sni)
                        addProperty("fp", fingerprint)
                    }
                    parserFactory.vmessConfigParser.encodeProtocol(VMESSConfig(
                        uuid = id,
                        tls = if (transportSecurity == "none") "" else transportSecurity,
                        host = wsHost,
                        network = network,
                        address = address,
                        others = others
                    ))
                }
                Protocol.SHADOW_SOCKS -> {
                    parserFactory.shadowSocksConfigParser.encodeProtocol(ShadowSocksConfig(
                        method = ssMethod,
                        password = id,
                        server = address,
                        port = port,
                        tag = remarks
                    ))
                }
                Protocol.TROJAN -> {
                    val params = mutableMapOf(
                        "type" to network,
                        "security" to transportSecurity
                    )
                    if (network == "ws") {
                        params["path"] = wsPath
                        params["host"] = wsHost
                    } else if (network == "grpc") {
                        params["serviceName"] = grpcServiceName
                    }
                    if (transportSecurity == "tls" || transportSecurity == "reality") {
                        params["sni"] = sni
                    }
                    parserFactory.trojanConfigParser.encodeProtocol(TrojanConfig(
                        scheme = "trojan",
                        password = id,
                        host = address,
                        port = port,
                        params = params,
                        remark = remarks,
                        original = ""
                    ))
                }

                Protocol.HYSTERIA2 -> {
                    "todo"
                }
            }
            
            val node = com.android.xrayfa.dto.Node(
                protocolPrefix = protocol.protocolType,
                address = address,
                port = port,
                remark = remarks,
                subscriptionId = -1, // Manual added
                url = url
            )
            nodeRepository.addNode(node)
        }
    }

}


class DetailViewmodelFactory
@Inject constructor(
    val parserFactory: ParserFactory,
    val nodeRepository: NodeRepository
): ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DetailViewmodel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DetailViewmodel(parserFactory,nodeRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
