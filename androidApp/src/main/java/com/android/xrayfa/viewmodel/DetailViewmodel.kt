package com.android.xrayfa.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.android.xrayfa.model.Node
import com.android.xrayfa.dto.VLESSConfig
import com.android.xrayfa.dto.VMESSConfig
import com.android.xrayfa.dto.ShadowSocksConfig
import com.android.xrayfa.dto.TrojanConfig
import com.android.xrayfa.dto.Hysteria2Config
import com.android.xrayfa.dto.SocksConfig
import com.android.xrayfa.dto.HttpConfig
import com.android.xrayfa.model.protocol.Protocol
import com.android.xrayfa.parser.ParserFactory
import com.android.xrayfa.repository.NodeRepository
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
    fun parseSocksProtocol(content:String): SocksConfig {
        return parserFactory.socksConfigParser.decodeProtocol(content)
    }
    fun parseHttpProtocol(content:String): HttpConfig {
        return parserFactory.httpConfigParser.decodeProtocol(content)
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
        allowInsecure: Boolean = false,
        username: String = ""
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
                    if (transportSecurity == "tls" && allowInsecure) {
                        params["allowInsecure"] = "1"
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
                    val others = buildJsonObject {
                        put("v", "2")
                        put("ps", remarks)
                        put("add", address)
                        put("port", port)
                        put("id", uuidOrPassword)
                        put("aid", "0")
                        put("scy", vmessSecurity)
                        put("net", network)
                        put("type", "none")
                        put("host", wsHost)
                        put(
                            "path",
                            if (network == "ws") wsPath else if (network == "grpc") grpcServiceName else "",
                        )
                        put("tls", if (transportSecurity == "none") "" else transportSecurity)
                        put("sni", sni)
                        put("fp", fingerprint)
                        if (transportSecurity == "tls" && allowInsecure) {
                            put("allowInsecure", "1")
                        }
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
                Protocol.SHADOWSOCKS -> {
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
                    if (transportSecurity == "tls" && allowInsecure) {
                        params["allowInsecure"] = "1"
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
                    if (allowInsecure) {
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

                Protocol.SOCKS -> {
                    parserFactory.socksConfigParser.encodeProtocol(
                        SocksConfig(
                            remark = remarks,
                            server = address,
                            port = port,
                            username = username.ifBlank { null },
                            password = uuidOrPassword.ifBlank { null }
                        )
                    )
                }

                Protocol.HTTP -> {
                    parserFactory.httpConfigParser.encodeProtocol(
                        HttpConfig(
                            remark = remarks,
                            server = address,
                            port = port,
                            username = username.ifBlank { null },
                            password = uuidOrPassword.ifBlank { null }
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


class DetailViewmodelFactory(
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
