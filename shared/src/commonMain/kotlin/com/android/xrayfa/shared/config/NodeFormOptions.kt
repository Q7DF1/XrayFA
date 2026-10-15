package com.android.xrayfa.shared.config

import com.android.xrayfa.model.protocol.Protocol
import com.android.xrayfa.parser.optionalString

/** Options implemented by the share-link parsers and the pinned Xray-core. */
object NodeFormOptions {
    val networks = listOf("raw", "xhttp", "ws", "grpc", "httpupgrade", "kcp")
    val methods = listOf(
        "aes-256-gcm", "aes-128-gcm", "chacha20-ietf-poly1305", "xchacha20-ietf-poly1305",
        "2022-blake3-aes-128-gcm", "2022-blake3-aes-256-gcm", "2022-blake3-chacha20-poly1305",
    )
    val fingerprints = listOf("", "chrome", "firefox", "safari", "edge", "android", "ios", "random", "randomized")
    val kcpKeys = listOf("mtu", "tti", "uplinkCapacity", "downlinkCapacity", "cwndMultiplier", "maxSendingWindow")
    val advancedKeys = kcpKeys.toSet() + setOf("headerType", "authority", "mode", "extra", "alpn", "pcs", "vcn", "ech", "spx", "pqv")

    fun canonicalNetwork(network: String): String = when (network) {
        "tcp" -> "raw"
        "websocket" -> "ws"
        "splithttp" -> "xhttp"
        "mkcp" -> "kcp"
        else -> network
    }

    fun canonicalMethod(method: String): String = when (method.lowercase()) {
        "aead_aes_128_gcm" -> "aes-128-gcm"
        "aead_aes_256_gcm" -> "aes-256-gcm"
        "chacha20-poly1305", "aead_chacha20_poly1305" -> "chacha20-ietf-poly1305"
        "xchacha20-poly1305", "aead_xchacha20_poly1305" -> "xchacha20-ietf-poly1305"
        else -> method.lowercase()
    }

    fun hasTransport(protocol: Protocol) = protocol in setOf(Protocol.VLESS, Protocol.VMESS, Protocol.TROJAN)

    fun securities(network: String): List<String> =
        if (canonicalNetwork(network) in setOf("raw", "xhttp", "grpc")) listOf("none", "tls", "reality")
        else listOf("none", "tls")

    fun advancedKeys(form: NodeEditForm): List<String> = buildList {
        if (hasTransport(form.selectedProtocol)) {
            when (canonicalNetwork(form.network)) {
                "raw" -> add("headerType")
                "kcp" -> addAll(kcpKeys)
                "grpc" -> addAll(listOf("authority", "mode"))
                "xhttp" -> addAll(listOf("mode", "extra"))
            }
        }
        if (form.selectedProtocol == Protocol.HYSTERIA2 ||
            (hasTransport(form.selectedProtocol) && form.transportSecurity == "tls")) {
            if (form.selectedProtocol != Protocol.HYSTERIA2) add("alpn")
            addAll(listOf("pcs", "vcn", "ech"))
        }
        if (hasTransport(form.selectedProtocol) && form.transportSecurity == "reality") addAll(listOf("spx", "pqv"))
    }

    fun advancedOptions(form: NodeEditForm, key: String): List<String>? = when (key) {
        "headerType" -> if (canonicalNetwork(form.network) == "raw") listOf("none", "http")
            else null
        "mode" -> if (canonicalNetwork(form.network) == "grpc") listOf("", "multi")
            else listOf("auto", "packet-up", "stream-up", "stream-one")
        else -> null
    }
}

fun NodeEditForm.advancedValue(key: String): String {
    advancedParams[key]?.let { return it }
    if (importedProtocol == selectedProtocol) {
        if (selectedProtocol == Protocol.VMESS) {
            importedVmess?.optionalString(if (key == "headerType") "type" else key)?.let { return it }
            if (key == "seed") importedVmess?.optionalString("path")?.let { return it }
        } else {
            importedParams[key]?.let { return it }
            if (key == "pqv") importedParams["mldsa65Verify"]?.let { return it }
            if (key == "seed") importedParams["path"]?.let { return it }
        }
    }
    return when (key) {
        "headerType" -> "none"
        "mode" -> if (NodeFormOptions.canonicalNetwork(network) == "xhttp") "auto" else ""
        else -> ""
    }
}

fun NodeEditForm.selectProtocol(protocol: Protocol): NodeEditForm = NodeEditForm(
    selectedProtocol = protocol,
    remarks = remarks,
    address = address,
    port = port,
    transportSecurity = if (protocol in setOf(Protocol.VLESS, Protocol.TROJAN, Protocol.HYSTERIA2)) "tls" else "none",
)
