package com.android.xrayfa.shared.config

import com.android.xrayfa.common.utils.Logger
import com.android.xrayfa.config.XrayJson
import com.android.xrayfa.dto.HttpConfig
import com.android.xrayfa.dto.Hysteria2Config
import com.android.xrayfa.dto.ParseLinkInput
import com.android.xrayfa.dto.ShadowSocksConfig
import com.android.xrayfa.dto.SocksConfig
import com.android.xrayfa.dto.TrojanConfig
import com.android.xrayfa.dto.VLESSConfig
import com.android.xrayfa.dto.VMESSConfig
import com.android.xrayfa.model.Node
import com.android.xrayfa.model.protocol.Protocol
import com.android.xrayfa.model.protocol.protocolPrefixMap
import com.android.xrayfa.model.serialization.OutboundObjectSerializer
import com.android.xrayfa.parser.ParserFactory
import com.android.xrayfa.parser.optionalString
import com.android.xrayfa.repository.NodeRepository
import com.android.xrayfa.shared.navigation.ConfigFilterIds
import com.android.xrayfa.vpn.VpnController
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
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
        val form =
            NodeEditForm(
                selectedProtocol = protocolPrefixMap[protocol] ?: Protocol.VLESS,
                transportSecurity = if (protocol == null || protocol in setOf("vless", "trojan", "hysteria2", "hy2")) "tls" else "none",
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
                        importedProtocol = Protocol.VLESS,
                        importedParams = config.param,
                        address = config.server,
                        port = config.port.toString(),
                        uuidOrPassword = config.uuid,
                        flow = config.param["flow"] ?: "",
                        vlessEncryption = config.param["encryption"] ?: "none",
                        network = NodeFormOptions.canonicalNetwork(config.param["type"] ?: "raw"),
                        transportSecurity = config.param["security"] ?: "none",
                        wsPath = config.param["path"] ?: "/",
                        wsHost = config.param["host"] ?: "",
                        grpcServiceName = config.param["serviceName"] ?: "",
                        sni = config.param["sni"] ?: "",
                        fingerprint = config.param["fp"] ?: "chrome",
                        publicKey = config.param["pbk"] ?: config.param["password"] ?: config.param["publicKey"] ?: "",
                        shortId = config.param["sid"] ?: "",
                    )
                }
                Protocol.VMESS.protocolType -> {
                    val config = parserFactory.vmessConfigParser.decodeProtocol(content)
                    val others = config.others
                    form.copy(
                        selectedProtocol = Protocol.VMESS,
                        importedProtocol = Protocol.VMESS,
                        importedVmess = others,
                        address = config.address,
                        port = others.optionalString("port") ?: "",
                        uuidOrPassword = config.uuid,
                        vmessSecurity = others.optionalString("scy") ?: "auto",
                        network = NodeFormOptions.canonicalNetwork(config.network),
                        transportSecurity = config.tls.ifBlank { "none" },
                        wsHost = config.host,
                        wsPath = others.optionalString("path") ?: "/",
                        grpcServiceName = others.optionalString("path") ?: "",
                        sni = others.optionalString("sni") ?: "",
                        fingerprint = others.optionalString("fp") ?: "chrome",
                        publicKey = others.optionalString("pbk") ?: others.optionalString("password") ?: others.optionalString("publicKey") ?: "",
                        shortId = others.optionalString("sid") ?: "",
                    )
                }
                Protocol.TROJAN.protocolType -> {
                    val config = parserFactory.trojanConfigParser.decodeProtocol(content)
                    form.copy(
                        selectedProtocol = Protocol.TROJAN,
                        importedProtocol = Protocol.TROJAN,
                        importedParams = config.params,
                        address = config.host ?: "",
                        port = config.port?.toString() ?: "",
                        uuidOrPassword = config.password,
                        network = NodeFormOptions.canonicalNetwork(config.params["type"] ?: "raw"),
                        transportSecurity = config.params["security"] ?: "tls",
                        wsPath = config.params["path"] ?: "/",
                        wsHost = config.params["host"] ?: "",
                        grpcServiceName = config.params["serviceName"] ?: "",
                        sni = config.params["sni"] ?: "",
                        fingerprint = config.params["fp"] ?: "chrome",
                        publicKey = config.params["pbk"] ?: config.params["password"] ?: config.params["publicKey"] ?: "",
                        shortId = config.params["sid"] ?: "",
                    )
                }
                Protocol.SHADOWSOCKS.protocolType -> {
                    val config = parserFactory.shadowSocksConfigParser.decodeProtocol(content)
                    form.copy(
                        selectedProtocol = Protocol.SHADOWSOCKS,
                        address = config.server,
                        port = config.port.toString(),
                        uuidOrPassword = config.password,
                        ssMethod = NodeFormOptions.canonicalMethod(config.method),
                    )
                }
                Protocol.HYSTERIA2.protocolType, "hy2" -> {
                    val config = parserFactory.hysteria2ConfigParser.decodeProtocol(content)
                    form.copy(
                        selectedProtocol = Protocol.HYSTERIA2,
                        importedProtocol = Protocol.HYSTERIA2,
                        importedParams = config.param,
                        address = config.address,
                        port = config.port.toString(),
                        uuidOrPassword = config.auth,
                        sni = config.param["sni"] ?: "",
                        hysteria2Alpn = config.param["alpn"] ?: "",
                        hysteria2Obfs = config.param["obfs"] ?: "",
                        hysteria2ObfsPassword = config.param["obfs-password"] ?: "",
                    )
                }
                Protocol.SOCKS.protocolType, "socks5" -> {
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
            logger.i(TAG, "Failed to parse node form")
            form
        }
    }

    suspend fun saveForm(
        nodeId: Int,
        form: NodeEditForm,
    ): Boolean {
        return try {
            require(validationIssue(form) == null) { "Invalid node form" }
            val url = encodeUrl(form)
            parserFactory.getParser(url).parseOutbound(url)
            val parsed = parserFactory.getParser(url).preParse(ParseLinkInput(
                protocolPrefix = form.selectedProtocol.protocolType,
                content = url,
                subscriptionId = ConfigFilterIds.SUB_MANUAL,
            ))
            val port = parsed.port
            val remarks = form.remarks

            if (nodeId > 0) {
                nodeRepository.updateNode(nodeId, url, port, remarks, parsed.address, parsed.countryISO)
                val selectedId = nodeRepository.querySelectedNode().first()?.id
                if (selectedId == nodeId) {
                    vpnController.restartIfNeeded()
                }
            } else {
                val node =
                    Node(
                        id = 0,
                        protocolPrefix = form.selectedProtocol.protocolType,
                        address = parsed.address,
                        countryISO = parsed.countryISO,
                        port = port,
                        remark = remarks,
                        subscriptionId = ConfigFilterIds.SUB_MANUAL,
                        url = url,
                    )
                nodeRepository.addNode(node)
            }
            true
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            logger.i(TAG, "Failed to save node form")
            false
        }
    }

    /** The same serializer used for the runtime configuration, without saving a node. */
    fun previewOutbound(form: NodeEditForm): String {
        require(validationIssue(form) == null) { "Invalid node form" }
        val url = encodeUrl(form)
        val outbound = parserFactory.getParser(url).parseOutbound(url)
        val json = XrayJson.encodeToString(OutboundObjectSerializer, outbound)
        return previewJson.encodeToString(JsonElement.serializer(), Json.parseToJsonElement(json))
    }

    internal fun encodeUrl(form: NodeEditForm): String {
        val protocol = form.selectedProtocol
        fun preservedParams(): MutableMap<String, String> {
            val params = if (form.importedProtocol == protocol) form.importedParams.toMutableMap()
                else mutableMapOf()
            val editedKeys = if (protocol == Protocol.HYSTERIA2) {
                listOf("sni", "obfs", "obfs-password", "alpn")
            } else {
                listOf("type", "security", "sni", "fp", "pbk", "publicKey", "password", "sid",
                    "encryption", "flow", "path", "host", "serviceName")
            }
            editedKeys.forEach(params::remove)
            NodeFormOptions.advancedKeys.forEach(params::remove)
            params.remove("mldsa65Verify")
            if (form.network in setOf("kcp", "mkcp") && form.importedProtocol == protocol) {
                // Keep legacy inputs until validation can explain that FinalMask migration is needed.
                form.importedParams["headerType"]?.let { params["headerType"] = it }
                form.importedParams["path"]?.let { params["path"] = it }
            }
            NodeFormOptions.advancedKeys(form).forEach { key ->
                form.advancedValue(key).takeIf(String::isNotBlank)?.let { params[key] = it }
            }
            if (form.network in setOf("tcp", "raw") && form.advancedValue("headerType") == "http") {
                params["path"] = form.wsPath
                params["host"] = form.wsHost
            }
            return params
        }
        return when (protocol) {
            Protocol.VLESS -> {
                val params =
                    preservedParams().apply {
                        putAll(
                            mapOf(
                                "type" to form.network,
                                "security" to form.transportSecurity,
                                "encryption" to form.vlessEncryption,
                                "flow" to form.flow,
                            ),
                        )
                    }
                if (form.network in setOf("ws", "websocket", "xhttp", "splithttp", "httpupgrade")) {
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
                        if (form.importedProtocol == protocol) form.importedVmess?.forEach { (key, value) -> put(key, value) }
                        put("v", "2")
                        put("ps", form.remarks)
                        put("add", form.address)
                        put("port", form.port.toIntOrNull() ?: 0)
                        put("id", form.uuidOrPassword)
                        put("aid", "0")
                        put("scy", form.vmessSecurity)
                        put("net", form.network)
                        NodeFormOptions.advancedKeys.forEach { key ->
                            // Clear inactive fields inherited from the imported JSON.
                            if (key != "headerType") put(key, "")
                        }
                        NodeFormOptions.advancedKeys(form).forEach { key ->
                            if (key != "headerType") put(key, form.advancedValue(key))
                        }
                        put("type", if (form.network in setOf("tcp", "raw", "kcp", "mkcp")) form.advancedValue("headerType") else "none")
                        put("host", form.wsHost)
                        put(
                            "path",
                            if (form.network in setOf("ws", "websocket", "xhttp", "splithttp", "httpupgrade", "tcp", "raw")) {
                                form.wsPath
                            } else if (form.network in setOf("kcp", "mkcp") && form.importedProtocol == protocol) {
                                form.importedVmess?.optionalString("path") ?: ""
                            } else if (form.network == "grpc") {
                                form.grpcServiceName
                            } else {
                                ""
                            },
                        )
                        put("tls", if (form.transportSecurity == "none") "" else form.transportSecurity)
                        put("sni", form.sni)
                        put("fp", form.fingerprint)
                        put("pbk", if (form.transportSecurity == "reality") form.publicKey else "")
                        put("sid", if (form.transportSecurity == "reality") form.shortId else "")
                        put("password", "")
                        put("publicKey", "")
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
                    preservedParams().apply {
                        putAll(
                            mapOf(
                                "type" to form.network,
                                "security" to form.transportSecurity,
                            ),
                        )
                    }
                if (form.network in setOf("ws", "websocket", "xhttp", "splithttp", "httpupgrade")) {
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
                val params = preservedParams()
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
        val previewJson = Json { prettyPrint = true }
    }
}
