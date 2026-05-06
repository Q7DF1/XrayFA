package com.android.xrayfa.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.android.xrayfa.dto.Node
import com.android.xrayfa.dto.VLESSConfig
import com.android.xrayfa.dto.VMESSConfig
import com.android.xrayfa.dto.ShadowSocksConfig
import com.android.xrayfa.dto.TrojanConfig
import com.android.xrayfa.dto.Hysteria2Config
import com.android.xrayfa.model.protocol.Protocol
import com.android.xrayfa.parser.ParserFactory
import com.android.xrayfa.repository.NodeRepository
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

class DetailViewmodel(
    val parserFactory: ParserFactory,
    val nodeRepository: NodeRepository,
): ViewModel() {

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
    fun parseHysteria2Protocol(content:String): Hysteria2Config {
        return parserFactory.hysteria2ConfigParser.decodeProtocol(content)
    }

    fun saveNode(
        nodeId: Int = -1,
        protocol: Protocol,
        remarks: String,
        address: String,
        port: Int,
        uuidOrPassword: String,
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
        shortId: String = "",
        hysteria2Obfs: String = "",
        hysteria2ObfsPassword: String = "",
        hysteria2Alpn: String = "",
        hysteria2AllowInsecure: Boolean = false
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
                        uuid = uuidOrPassword,
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
                        addProperty("id", uuidOrPassword)
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
                        uuid = uuidOrPassword,
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
                        password = uuidOrPassword,
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
                        password = uuidOrPassword,
                        host = address,
                        port = port,
                        params = params,
                        remark = remarks,
                        original = ""
                    ))
                }

                Protocol.HYSTERIA2 -> {
                    val params = mutableMapOf<String, String>()
                    if (sni.isNotBlank()) {
                        params["sni"] = sni
                    }
                    if (hysteria2Alpn.isNotBlank()) {
                        params["alpn"] = hysteria2Alpn
                    }
                    if (hysteria2Obfs.isNotBlank()) {
                        params["obfs"] = hysteria2Obfs
                    }
                    if (hysteria2ObfsPassword.isNotBlank()) {
                        params["obfs-password"] = hysteria2ObfsPassword
                    }
                    if (hysteria2AllowInsecure) {
                        params["allowInsecure"] = "1"
                    }
                    parserFactory.hysteria2ConfigParser.encodeProtocol(
                        Hysteria2Config(
                            remark = remarks,
                            address = address,
                            port = port,
                            auth = uuidOrPassword,
                            param = params
                        )
                    )
                }
            }
            
            if (nodeId > 0) {
                nodeRepository.updateNode(nodeId, url, port,remarks)
            } else {
                val node = Node(
                    id = 0,
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
