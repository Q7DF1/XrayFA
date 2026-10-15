package com.android.xrayfa.parser

import com.android.xrayfa.model.stream.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

internal fun Map<String, String>.nonBlank(key: String): String? = get(key)?.takeIf { it.isNotBlank() }

internal fun shareLinkTls(params: Map<String, String>, address: String): TlsSettings {
    for (key in listOf("allowInsecure", "insecure", "skip-cert-verify")) {
        require(params[key]?.trim()?.lowercase() !in setOf("1", "true")) {
            "Xray no longer supports $key=true; use a valid certificate, pcs (certificate SHA-256) or vcn (certificate name)"
        }
    }
    return TlsSettings(
        serverName = params.nonBlank("sni") ?: params.nonBlank("peer") ?: params.nonBlank("host") ?: address,
        alpn = params.nonBlank("alpn")?.split(',')?.map(String::trim)?.filter(String::isNotEmpty),
        fingerprint = params.nonBlank("fp"),
        pinnedPeerCertSha256 = params.nonBlank("pcs"),
        verifyPeerCertByName = params.nonBlank("vcn"),
        echConfigList = params.nonBlank("ech"),
    )
}

internal fun shareLinkStreamSettings(
    params: Map<String, String>, address: String, defaultSecurity: String = "none",
): StreamSettingsObject {
    val network = when (val type = params.nonBlank("type")?.lowercase() ?: "raw") {
        "tcp", "raw" -> "raw"
        "websocket", "ws" -> "ws"
        "splithttp", "xhttp" -> "xhttp"
        "mkcp", "kcp" -> "kcp"
        "grpc", "httpupgrade" -> type
        else -> throw IllegalArgumentException("Unsupported transport: $type; configure a transport supported by the server and Xray")
    }
    val security = params.nonBlank("security")?.lowercase() ?: defaultSecurity
    require(security in setOf("none", "tls", "reality")) { "Unsupported security: $security" }
    require(security != "reality" || network in setOf("raw", "xhttp", "grpc")) {
        "REALITY only supports RAW, XHTTP and gRPC"
    }
    val tls = if (security == "tls") shareLinkTls(params, address) else null
    val reality = if (security == "reality") {
        val key = params.nonBlank("pbk") ?: params.nonBlank("password") ?: params.nonBlank("publicKey")
        require(key != null) { "REALITY public key (pbk) is required" }
        RealitySettings(
            fingerprint = params.nonBlank("fp") ?: "chrome",
            password = key,
            serverName = params.nonBlank("sni") ?: address,
            shortId = params["sid"] ?: "",
            spiderX = params.nonBlank("spx") ?: "/",
            mldsa65Verify = params.nonBlank("pqv") ?: params.nonBlank("mldsa65Verify"),
        )
    } else null
    val path = params.nonBlank("path") ?: "/"
    val host = params["host"] ?: ""
    val xhttp = if (network == "xhttp") {
        val mode = params.nonBlank("mode") ?: "auto"
        require(mode in setOf("auto", "packet-up", "stream-up", "stream-one")) { "Unsupported XHTTP mode: $mode" }
        val extra = params.nonBlank("extra")?.let {
            Json.parseToJsonElement(it) as? JsonObject
                ?: throw IllegalArgumentException("XHTTP extra must be a JSON object")
        }
        XHttpSettings(mode = mode, path = path, host = host, extra = extra)
    } else null
    val headerType = params.nonBlank("headerType") ?: "none"
    val kcp = if (network == "kcp") {
        require(headerType == "none" && params.nonBlank("seed") == null && params.nonBlank("path") == null) {
            "Legacy mKCP header/seed are no longer consumed by Xray; configure finalmask in a JSON configuration"
        }
        fun uint(key: String, minimum: Long = 0, maximum: Long = 4294967295): Long? = params.nonBlank(key)?.let {
            it.toLongOrNull()?.takeIf { value -> value in minimum..maximum }
                ?: throw IllegalArgumentException("Invalid mKCP $key")
        }
        val mtu = uint("mtu", 21)
        val window = uint("maxSendingWindow", mtu ?: 1350)
        KcpSettings(mtu = mtu, tti = uint("tti", 10, 1000),
            uplinkCapacity = uint("uplinkCapacity"), downlinkCapacity = uint("downlinkCapacity"),
            cwndMultiplier = uint("cwndMultiplier", 1), maxSendingWindow = window)
    } else null
    return StreamSettingsObject(
        network = network,
        security = security,
        tlsSettings = tls,
        realitySettings = reality,
        rawSettings = if (network == "raw") {
            require(headerType in setOf("none", "http")) { "Unsupported RAW header: $headerType" }
            RawSettings(header = if (headerType == "http") HttpHeaderObject(
                request = HttpRequestObject(
                    path = path.split(','),
                    headers = mapOf("Host" to host.split(',').filter(String::isNotEmpty)),
                ),
            ) else NoneHeaderObject())
        } else null,
        wsSettings = if (network == "ws") WsSettings(path = path, host = host) else null,
        grpcSettings = if (network == "grpc") GrpcSettings(
            serviceName = params["serviceName"] ?: "",
            authority = params.nonBlank("authority"),
            multiMode = params["mode"] == "multi",
        ) else null,
        xhttpSettings = xhttp,
        httpUpgradeSettings = if (network == "httpupgrade") HttpUpgradeSettings(path = path, host = host) else null,
        kcpSettings = kcp,
    )
}
