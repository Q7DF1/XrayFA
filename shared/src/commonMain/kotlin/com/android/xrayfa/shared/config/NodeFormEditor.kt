package com.android.xrayfa.shared.config

import com.android.xrayfa.common.utils.Logger
import com.android.xrayfa.dto.Hysteria2Config
import com.android.xrayfa.dto.HttpConfig
import com.android.xrayfa.dto.ShadowSocksConfig
import com.android.xrayfa.dto.SocksConfig
import com.android.xrayfa.dto.TrojanConfig
import com.android.xrayfa.dto.VLESSConfig
import com.android.xrayfa.dto.VMESSConfig
import com.android.xrayfa.model.Node
import com.android.xrayfa.model.protocol.Protocol
import com.android.xrayfa.parser.ParserFactory
import com.android.xrayfa.parser.optionalString
import com.android.xrayfa.repository.NodeRepository
import com.android.xrayfa.shared.navigation.ConfigFilterIds
import com.android.xrayfa.vpn.VpnController
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class NodeFormEditor(
    private val parserFactory: ParserFactory,
    private val nodeRepository: NodeRepository,
    private val vpnController: VpnController,
    private val logger: Logger,
) {
    fun parseForm(
        protocol: String?,
        content: String?,
        remark: String? = null,
    ): NodeEditForm {
        var form =
            NodeEditForm(
                remarks = remark.orEmpty(),
            )

        if (content.isNullOrBlank()) {
            return form
        }

        return try {
            when (protocol) {
                Protocol.VLESS.protocolType -> {
                    val config = parserFactory.vlessConfigParser.decodeProtocol(content)
                    form.copy(
                        selectedProtocol = Protocol.VLESS,
                        address = config.server,
                        port = config.port.toString(),
                        uuidOrPassword = config.uuid,
                        flow = config.param["flow"] ?: "",
                        vlessEncryption = config.param["encryption"] ?: "none",
                        network = config.param["type"] ?: "tcp",
                        transportSecurity = config.param["security"] ?: "none",
                        wsPath = config.param["path"] ?: "/",
                        wsHost = config.param["host"] ?: "",
                        grpcServiceName = config.param["serviceName"] ?: "",
                        sni = config.param["sni"] ?: "",
                        fingerprint = config.param["fp"] ?: "chrome",
                        publicKey = config.param["pbk"] ?: "",
                        shortId = config.param["sid"] ?: "",
                    )
                }
                Protocol.VMESS.protocolType -> {
                    val config = parserFactory.vmessConfigParser.decodeProtocol(content)
                    val others = config.others
                    form.copy(
                        selectedProtocol = Protocol.VMESS,
                        address = config.address,
                        port = others.optionalString("port") ?: "",
                        uuidOrPassword = config.uuid,
                        vmessSecurity = others.optionalString("scy") ?: "auto",
                        network = config.network,
                        transportSecurity = config.tls,
                        wsHost = config.host,
                        wsPath = others.optionalString("path") ?: "/",
                        sni = others.optionalString("sni") ?: "",
                        fingerprint = others.optionalString("fp") ?: "chrome",
                    )
                }
                Protocol.TROJAN.protocolType -> {
                    val config = parserFactory.trojanConfigParser.decodeProtocol(content)
                    form.copy(
                        selectedProtocol = Protocol.TROJAN,
                        address = config.host ?: "",
                        port = config.port?.toString() ?: "",
                        uuidOrPassword = config.password,
                        network = config.params["type"] ?: "tcp",
                        transportSecurity = config.params["security"] ?: "none",
                        wsPath = config.params["path"] ?: "/",
                        wsHost = config.params["host"] ?: "",
                        grpcServiceName = config.params["serviceName"] ?: "",
                        sni = config.params["sni"] ?: "",
                    )
                }
                Protocol.SHADOWSOCKS.protocolType -> {
                    val config = parserFactory.shadowSocksConfigParser.decodeProtocol(content)
                    form.copy(
                        selectedProtocol = Protocol.SHADOWSOCKS,
                        address = config.server,
                        port = config.port.toString(),
                        uuidOrPassword = config.password,
                        ssMethod = config.method,
                    )
                }
                Protocol.HYSTERIA2.protocolType -> {
                    val config = parserFactory.hysteria2ConfigParser.decodeProtocol(content)
                    form.copy(
                        selectedProtocol = Protocol.HYSTERIA2,
                        address = config.address,
                        port = config.port.toString(),
                        uuidOrPassword = config.auth,
                        sni = config.param["sni"] ?: "",
                        hysteria2Alpn = config.param["alpn"] ?: "",
                        hysteria2Obfs = config.param["obfs"] ?: "",
                        hysteria2ObfsPassword = config.param["obfs-password"] ?: "",
                    )
                }
                Protocol.SOCKS.protocolType -> {
                    val config = parserFactory.socksConfigParser.decodeProtocol(content)
                    form.copy(
                        selectedProtocol = Protocol.SOCKS,
                        address = config.server,
                        port = config.port.toString(),
                        username = config.username ?: "",
                        uuidOrPassword = config.password ?: "",
                    )
                }
                Protocol.HTTP.protocolType -> {
                    val config = parserFactory.httpConfigParser.decodeProtocol(content)
                    form.copy(
                        selectedProtocol = Protocol.HTTP,
                        address = config.server,
                        port = config.port.toString(),
                        username = config.username ?: "",
                        uuidOrPassword = config.password ?: "",
                    )
                }
                else -> form
            }
        } catch (e: Exception) {
            logger.i(TAG, "Failed to parse node form: ${e.message}")
            form
        }
    }

    suspend fun saveForm(
        nodeId: Int,
        form: NodeEditForm,
    ): Boolean {
        return try {
            val url = encodeUrl(form)
            val port = form.port.toIntOrNull() ?: 0
            val remarks = form.remarks

            if (nodeId > 0) {
                nodeRepository.updateNode(nodeId, url, port, remarks)
                val selectedId = nodeRepository.querySelectedNode().first()?.id
                if (selectedId == nodeId) {
                    vpnController.restartIfNeeded()
                }
            } else {
                val node =
                    Node(
                        id = 0,
                        protocolPrefix = form.selectedProtocol.protocolType,
                        address = form.address,
                        port = port,
                        remark = remarks,
                        subscriptionId = ConfigFilterIds.SUB_MANUAL,
                        url = url,
                    )
                nodeRepository.addNode(node)
            }
            true
        } catch (e: Exception) {
            logger.i(TAG, "Failed to save node form: ${e.message}")
            false
        }
    }

    private fun encodeUrl(form: NodeEditForm): String {
        val protocol = form.selectedProtocol
        return when (protocol) {
            Protocol.VLESS -> {
                val params =
                    mutableMapOf(
                        "type" to form.network,
                        "security" to form.transportSecurity,
                        "encryption" to form.vlessEncryption,
                        "flow" to form.flow,
                    )
                if (form.network == "ws") {
                    params["path"] = form.wsPath
                    params["host"] = form.wsHost
                } else if (form.network == "grpc") {
                    params["serviceName"] = form.grpcServiceName
                }
                if (form.transportSecurity == "tls" || form.transportSecurity == "reality") {
                    params["sni"] = form.sni
                    params["fp"] = form.fingerprint
                }
                if (form.transportSecurity == "reality") {
                    params["pbk"] = form.publicKey
                    params["sid"] = form.shortId
                }
                parserFactory.vlessConfigParser.encodeProtocol(
                    VLESSConfig(
                        remark = form.remarks,
                        uuid = form.uuidOrPassword,
                        server = form.address,
                        port = form.port.toIntOrNull() ?: 0,
                        param = params,
                    ),
                )
            }
            Protocol.VMESS -> {
                val others =
                    buildJsonObject {
                        put("v", "2")
                        put("ps", form.remarks)
                        put("add", form.address)
                        put("port", form.port.toIntOrNull() ?: 0)
                        put("id", form.uuidOrPassword)
                        put("aid", "0")
                        put("scy", form.vmessSecurity)
                        put("net", form.network)
                        put("type", "none")
                        put("host", form.wsHost)
                        put(
                            "path",
                            if (form.network == "ws") {
                                form.wsPath
                            } else if (form.network == "grpc") {
                                form.grpcServiceName
                            } else {
                                ""
                            },
                        )
                        put("tls", if (form.transportSecurity == "none") "" else form.transportSecurity)
                        put("sni", form.sni)
                        put("fp", form.fingerprint)
                    }
                parserFactory.vmessConfigParser.encodeProtocol(
                    VMESSConfig(
                        uuid = form.uuidOrPassword,
                        tls = if (form.transportSecurity == "none") "" else form.transportSecurity,
                        host = form.wsHost,
                        network = form.network,
                        address = form.address,
                        others = others,
                    ),
                )
            }
            Protocol.SHADOWSOCKS ->
                parserFactory.shadowSocksConfigParser.encodeProtocol(
                    ShadowSocksConfig(
                        method = form.ssMethod,
                        password = form.uuidOrPassword,
                        server = form.address,
                        port = form.port.toIntOrNull() ?: 0,
                        tag = form.remarks,
                    ),
                )
            Protocol.TROJAN -> {
                val params =
                    mutableMapOf(
                        "type" to form.network,
                        "security" to form.transportSecurity,
                    )
                if (form.network == "ws") {
                    params["path"] = form.wsPath
                    params["host"] = form.wsHost
                } else if (form.network == "grpc") {
                    params["serviceName"] = form.grpcServiceName
                }
                if (form.transportSecurity == "tls" || form.transportSecurity == "reality") {
                    params["sni"] = form.sni
                }
                parserFactory.trojanConfigParser.encodeProtocol(
                    TrojanConfig(
                        scheme = "trojan",
                        password = form.uuidOrPassword,
                        host = form.address,
                        port = form.port.toIntOrNull(),
                        params = params,
                        remark = form.remarks,
                        original = "",
                    ),
                )
            }
            Protocol.HYSTERIA2 -> {
                val params = mutableMapOf<String, String>()
                if (form.sni.isNotBlank()) {
                    params["sni"] = form.sni
                }
                if (form.hysteria2Alpn.isNotBlank()) {
                    params["alpn"] = form.hysteria2Alpn
                }
                if (form.hysteria2Obfs.isNotBlank()) {
                    params["obfs"] = form.hysteria2Obfs
                }
                if (form.hysteria2ObfsPassword.isNotBlank()) {
                    params["obfs-password"] = form.hysteria2ObfsPassword
                }
                parserFactory.hysteria2ConfigParser.encodeProtocol(
                    Hysteria2Config(
                        remark = form.remarks,
                        address = form.address,
                        port = form.port.toIntOrNull() ?: 0,
                        auth = form.uuidOrPassword,
                        param = params,
                    ),
                )
            }
            Protocol.SOCKS ->
                parserFactory.socksConfigParser.encodeProtocol(
                    SocksConfig(
                        remark = form.remarks,
                        server = form.address,
                        port = form.port.toIntOrNull() ?: 0,
                        username = form.username.ifBlank { null },
                        password = form.uuidOrPassword.ifBlank { null },
                    ),
                )
            Protocol.HTTP ->
                parserFactory.httpConfigParser.encodeProtocol(
                    HttpConfig(
                        remark = form.remarks,
                        server = form.address,
                        port = form.port.toIntOrNull() ?: 0,
                        username = form.username.ifBlank { null },
                        password = form.uuidOrPassword.ifBlank { null },
                    ),
                )
        }
    }

    private companion object {
        const val TAG = "NodeFormEditor"
    }
}
