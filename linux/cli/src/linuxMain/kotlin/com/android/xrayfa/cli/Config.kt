package com.android.xrayfa.cli

import com.android.xrayfa.common.core.GeoIpProvider
import com.android.xrayfa.common.repository.*
import com.android.xrayfa.common.utils.*
import com.android.xrayfa.config.*
import com.android.xrayfa.dto.ParseLinkInput
import com.android.xrayfa.model.Node
import com.android.xrayfa.model.serialization.OutboundObjectSerializer
import com.android.xrayfa.parser.*
import kotlinx.serialization.json.*

internal class NodeParser {
    private val settings =
        object : ConfigParserSettingsProvider {
            override suspend fun getConfigParserSettings() =
                ConfigParserSettings(
                    socksListen = "127.0.0.1",
                    socksPort = 10808,
                    socksUserName = "",
                    socksPassword = "",
                    lanHttpProxyEnable = false,
                    httpPort = 10809,
                    ipV6Enable = false,
                    domainStrategy = 0,
                    routingRules = "",
                    routingMode = 0,
                    geoLiteInstall = false,
                )
        }
    private val geo =
        object : GeoIpProvider {
            override fun countryIsoFromIp(ip: String) = ""
        }
    private val encoder = KotlinxXrayConfigEncoder()
    private val factory =
        ParserFactory(
            VLESSConfigParser(settings, geo, encoder),
            VMESSConfigParser(settings, geo, encoder),
            TrojanConfigParser(settings, geo, encoder),
            ShadowSocksConfigParser(settings, geo, encoder),
            Hysteria2ConfigParser(settings, geo, encoder),
            SocksConfigParser(settings, geo, encoder),
            HttpConfigParser(settings, geo, encoder),
        )
    val adaptations = mutableSetOf<String>()

    private fun parameters(link: String): Map<String, String> =
        link
            .substringBefore('#')
            .substringAfter('?', "")
            .split('&')
            .filter { it.isNotEmpty() }
            .associate {
                UrlCodec.decode(it.substringBefore('=')) to
                    UrlCodec.decode(it.substringAfter('=', ""))
            }

    private fun params(link: String): Map<String, String> {
        if (!link.startsWith("vmess://")) return parameters(link)
        val j =
            json
                .parseToJsonElement(
                    Base64Compat.decode(link.removePrefix("vmess://")).decodeToString()
                )
                .jsonObject
        return j.mapValues { it.value.jsonPrimitive.content } +
            mapOf(
                "type" to (j["net"]?.jsonPrimitive?.content ?: "raw"),
                "security" to (j["tls"]?.jsonPrimitive?.content?.ifBlank { "none" } ?: "none"),
                "headerType" to (j["type"]?.jsonPrimitive?.content ?: "none"),
            )
    }

    private fun validateInput(link: String) {
        require(link.length in 1..1048576 && link.none { it.code < 32 || it.code == 127 }) {
            "Invalid link length or control character"
        }
        require(!Regex("%(?![0-9a-fA-F]{2})").containsMatchIn(link)) { "Invalid percent encoding" }
        val scheme = link.substringBefore("://")
        val p = params(link)
        require(
            listOf("allowInsecure", "insecure", "skip-cert-verify").none {
                p[it]?.trim()?.lowercase() in setOf("1", "true")
            }
        ) {
            "Insecure TLS verification is unsupported"
        }
        if (scheme in setOf("socks", "socks5", "http", "ss"))
            require(p.isEmpty()) { "Proxy transport/TLS/plugin parameters are unsupported" }
        val commonKeys =
            setOf(
                "type",
                "security",
                "sni",
                "peer",
                "host",
                "path",
                "headerType",
                "seed",
                "mode",
                "serviceName",
                "authority",
                "extra",
                "mtu",
                "tti",
                "uplinkCapacity",
                "downlinkCapacity",
                "cwndMultiplier",
                "maxSendingWindow",
                "fp",
                "alpn",
                "pcs",
                "vcn",
                "ech",
                "allowInsecure",
                "insecure",
                "skip-cert-verify",
                "pbk",
                "password",
                "publicKey",
                "sid",
                "spx",
                "pqv",
                "mldsa65Verify",
            )
        val allowed =
            when (scheme) {
                "vless" -> commonKeys + setOf("flow", "encryption")
                "trojan" -> commonKeys
                "vmess" ->
                    commonKeys + setOf("v", "ps", "id", "add", "port", "net", "tls", "scy", "aid")
                "hy2",
                "hysteria2" ->
                    setOf(
                        "type",
                        "security",
                        "sni",
                        "peer",
                        "host",
                        "fp",
                        "alpn",
                        "pcs",
                        "vcn",
                        "ech",
                        "allowInsecure",
                        "insecure",
                        "skip-cert-verify",
                        "obfs",
                        "obfs-password",
                    )
                else -> emptySet()
            }
        require(p.keys.all { it in allowed }) { "Unsupported link parameter" }
        if (scheme in setOf("hysteria2", "hy2")) {
            require(
                p["type"]?.let { it == "hysteria" } != false &&
                    p["security"]?.let { it == "tls" } != false
            ) {
                "Hysteria2 requires hysteria transport and TLS"
            }
            require(p["obfs-password"].isNullOrEmpty() || p["obfs"] == "salamander") {
                "Orphan obfuscation password"
            }
        }
        val t =
            when (p["type"]) {
                null,
                "",
                "tcp" -> "raw"
                "websocket" -> "ws"
                "splithttp" -> "xhttp"
                "mkcp" -> "kcp"
                else -> p["type"]
            }
        if (t == "grpc")
            require(p["mode"].isNullOrEmpty() || p["mode"] in setOf("gun", "multi")) {
                "Invalid gRPC mode"
            }
        if (p["security"] == "reality") {
            val key =
                sequenceOf("pbk", "password", "publicKey")
                    .mapNotNull { p[it]?.takeIf { v -> v.isNotBlank() } }
                    .firstOrNull()
            require(
                key != null &&
                    key.length == 43 &&
                    Regex("[A-Za-z0-9_-]{43}").matches(key) &&
                    Base64Compat.decode(key.replace('-', '+').replace('_', '/')).size == 32
            ) {
                "Invalid REALITY public key"
            }
            val sid = p["sid"] ?: ""
            require(
                sid.length <= 16 &&
                    sid.length % 2 == 0 &&
                    sid.all { it in "0123456789abcdefABCDEF" }
            ) {
                "Invalid REALITY short ID"
            }
            for (k in listOf("pqv", "mldsa65Verify")) p[k]
                ?.takeIf { it.isNotEmpty() }
                ?.let {
                    require(
                        Base64Compat.decode(it.replace('-', '+').replace('_', '/')).size == 1952
                    ) {
                        "Invalid REALITY ML-DSA-65 verification key"
                    }
                }
        }
        if (scheme == "vless") {
            val flow = p["flow"].orEmpty()
            require(flow in setOf("", "xtls-rprx-vision", "xtls-rprx-vision-udp443")) {
                "Invalid VLESS flow"
            }
            require(
                flow.isEmpty() ||
                    p["encryption"]?.let { it != "none" } == true ||
                    (t == "raw" && p["security"] in setOf("tls", "reality"))
            ) {
                "Vision requires RAW TLS/REALITY"
            }
        }
        if (p["security"] == "tls" || scheme in setOf("hysteria2", "hy2")) {
            require(
                p["fp"].isNullOrEmpty() ||
                    p["fp"] in
                        setOf(
                            "chrome",
                            "firefox",
                            "safari",
                            "edge",
                            "android",
                            "ios",
                            "random",
                            "randomized",
                        )
            ) {
                "Invalid TLS fingerprint"
            }
            p["ech"]
                ?.takeIf { it.isNotBlank() }
                ?.let {
                    val b = Base64Compat.decode(it)
                    require(
                        b.size >= 6 &&
                            ((b[0].toInt() and 255) * 256 + (b[1].toInt() and 255)) == b.size - 2
                    ) {
                        "Invalid ECHConfigList encoding"
                    }
                }
            p["pcs"]
                ?.split(',')
                ?.filter { it.isNotBlank() }
                ?.forEach {
                    require(Regex("[a-fA-F0-9]{64}").matches(it.trim().replace(":", ""))) {
                        "Invalid certificate SHA-256 pin"
                    }
                }
        }
        if (scheme == "vmess")
            require(
                p["scy"] == null ||
                    p["scy"] in setOf("auto", "aes-128-gcm", "chacha20-poly1305", "none", "zero")
            ) {
                "Invalid VMess cipher"
            }
        if (scheme in setOf("http", "socks", "socks5")) {
            val authority =
                link
                    .substringAfter("://")
                    .substringBefore('/')
                    .substringBefore('?')
                    .substringBefore('#')
                    .substringAfterLast('@')
            val explicit =
                if (authority.startsWith('[')) authority.substringAfter(']', "").removePrefix(":")
                else if (':' in authority) authority.substringAfterLast(':') else null
            require(
                explicit == null ||
                    explicit.isEmpty() && !authority.endsWith(':') ||
                    explicit.toIntOrNull() in 1..65535
            ) {
                "Invalid explicit proxy port"
            }
        }
    }

    private fun normalize(link: String): String {
        if (!link.startsWith("ss://")) return link
        val parser = factory.shadowSocksConfigParser
        val c = parser.decodeProtocol(link)
        val method =
            when (c.method.lowercase()) {
                "aead_aes_128_gcm" -> "aes-128-gcm"
                "aead_aes_256_gcm" -> "aes-256-gcm"
                "chacha20-poly1305",
                "aead_chacha20_poly1305" -> "chacha20-ietf-poly1305"
                "xchacha20-poly1305",
                "aead_xchacha20_poly1305" -> "xchacha20-ietf-poly1305"
                else -> c.method.lowercase()
            }
        require(
            method in
                setOf(
                    "aes-128-gcm",
                    "aes-256-gcm",
                    "chacha20-ietf-poly1305",
                    "xchacha20-ietf-poly1305",
                    "2022-blake3-aes-128-gcm",
                    "2022-blake3-aes-256-gcm",
                    "2022-blake3-chacha20-poly1305",
                )
        ) {
            "Unknown Shadowsocks method"
        }
        require(c.password.isNotEmpty()) { "Shadowsocks password is required" }
        if (method.startsWith("2022")) {
            val size = if (method == "2022-blake3-aes-128-gcm") 16 else 32
            require(Base64Compat.decode(c.password).size == size) {
                "Invalid Shadowsocks 2022 key length"
            }
        }
        if (method != c.method) {
            adaptations.add("COMP-03: Shadowsocks method alias normalized in Linux")
            return parser.encodeProtocol(c.copy(method = method))
        }
        return link
    }

    private fun validateOutbound(out: JsonObject) {
        val protocol = out.getValue("protocol").jsonPrimitive.content
        val stream = out["streamSettings"]?.jsonObject
        val secure = stream?.get("security")?.jsonPrimitive?.content in setOf("tls", "reality")
        if (protocol in setOf("vless", "trojan") && !secure) {
            val settings = out.getValue("settings").jsonObject
            val server =
                settings
                    .getValue(if (protocol == "vless") "vnext" else "servers")
                    .jsonArray[0]
                    .jsonObject
            val enc =
                if (protocol == "vless")
                    server
                        .getValue("users")
                        .jsonArray[0]
                        .jsonObject["encryption"]
                        ?.jsonPrimitive
                        ?.content
                else "none"
            require(
                enc != "none" ||
                    isXrayPrivateEndpoint(server.getValue("address").jsonPrimitive.content)
            ) {
                "Unencrypted transport requires a private server endpoint"
            }
        }
    }

    suspend fun parse(link: String): Node {
        validateInput(link)
        val normalized = normalize(link)
        val parser = factory.getParser(normalized)
        val node =
            parser.preParse(
                ParseLinkInput(
                    protocolPrefix = link.substringBefore("://"),
                    content = normalized,
                    subscriptionId = 0,
                )
            )
        require(node.address.isNotBlank() && node.port in 1..65535) { "Invalid node endpoint" }
        validateOutbound(outbound(link))
        // Keep original link. Normalization is applied again from the stored snapshot.
        return node.copy(url = link)
    }

    fun outbound(link: String): JsonObject {
        validateInput(link)
        val value = factory.getParser(normalize(link)).parseOutbound(normalize(link))
        return XrayJson.encodeToJsonElement(OutboundObjectSerializer, value)
            .jsonObject
            .also(::validateOutbound)
    }
}

internal fun nodeRecord(node: Node, id: Int) = buildJsonObject {
    put("id", id)
    put("url", node.url)
    put("protocol", node.protocolPrefix)
    put("address", node.address)
    put("port", node.port)
    put("name", node.remark ?: "${node.protocolPrefix}-${node.address}")
}

internal fun summary(node: JsonObject): String {
    val secrets =
        try {
            secretValues(NodeParser().outbound(node.getValue("url").jsonPrimitive.content))
        } catch (_: Exception) {
            emptySet()
        }
    return "${node.getValue("id").jsonPrimitive.int}: name=${redact(node.getValue("name").jsonPrimitive.content,secrets)} protocol=${node.getValue("protocol").jsonPrimitive.content} address=${clean(node.getValue("address").jsonPrimitive.content)} port=${node.getValue("port").jsonPrimitive.int}"
}
