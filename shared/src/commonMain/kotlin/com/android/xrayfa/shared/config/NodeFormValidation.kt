package com.android.xrayfa.shared.config

import com.android.xrayfa.common.utils.Base64Compat
import com.android.xrayfa.model.protocol.Protocol
import com.android.xrayfa.parser.isXrayPrivateEndpoint
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

enum class NodeFormIssueKind { REQUIRED, INVALID, INCOMPATIBLE, SECURITY_REQUIRED, LEGACY_KCP }
data class NodeFormIssue(val field: String, val kind: NodeFormIssueKind)

/** Structural checks for the form; the parser subsequently builds the outbound. */
fun NodeFormEditor.validationIssue(form: NodeEditForm): NodeFormIssue? {
    fun required(field: String) = NodeFormIssue(field, NodeFormIssueKind.REQUIRED)
    fun invalid(field: String) = NodeFormIssue(field, NodeFormIssueKind.INVALID)
    fun incompatible(field: String) = NodeFormIssue(field, NodeFormIssueKind.INCOMPATIBLE)
    if (form.address.isBlank()) return required("address")
    if (form.address.any { it.isWhitespace() } || form.address.contains("://") || form.address.any { it in "/?#@" }) return invalid("address")
    if (form.port.toIntOrNull() !in 1..65535) return invalid("port")
    val protocol = form.selectedProtocol
    if (protocol !in setOf(Protocol.HTTP, Protocol.SOCKS) && form.uuidOrPassword.isBlank()) {
        return required(if (protocol in setOf(Protocol.VLESS, Protocol.VMESS)) "id" else "password")
    }
    if (protocol in setOf(Protocol.VLESS, Protocol.VMESS)) {
        val id = form.uuidOrPassword
        val uuid = Regex("[0-9a-fA-F]{8}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{12}")
        if (!uuid.matches(id) && id.encodeToByteArray().size !in 1..30) return invalid("id")
    }
    if (protocol in setOf(Protocol.HTTP, Protocol.SOCKS) && form.username.isBlank() && form.uuidOrPassword.isNotBlank()) return required("user")
    if (protocol == Protocol.SHADOWSOCKS) {
        if (form.ssMethod !in NodeFormOptions.methods) return invalid("method")
        if (form.ssMethod.startsWith("2022-")) {
            val sizes = if (form.ssMethod == "2022-blake3-aes-128-gcm") setOf(16, 32) else setOf(32)
            if (form.uuidOrPassword.split(':').any { key ->
                runCatching { Base64Compat.decode(key).size }.getOrNull() !in sizes
            }) return invalid("password")
        }
    }
    if (protocol == Protocol.HYSTERIA2) {
        if (form.hysteria2Obfs !in setOf("", "salamander")) return invalid("obfs")
        if (form.hysteria2Obfs == "salamander" && form.hysteria2ObfsPassword.isBlank()) return required("obfs-password")
    }
    if (NodeFormOptions.hasTransport(protocol)) {
        val network = NodeFormOptions.canonicalNetwork(form.network)
        if (network !in NodeFormOptions.networks) return invalid("network")
        if (form.transportSecurity !in NodeFormOptions.securities(network)) return incompatible("security / network")
        if (protocol in setOf(Protocol.VLESS, Protocol.TROJAN) && form.transportSecurity == "none" &&
            (protocol != Protocol.VLESS || form.vlessEncryption == "none") && !isXrayPrivateEndpoint(form.address)) {
            return NodeFormIssue("security / address", NodeFormIssueKind.SECURITY_REQUIRED)
        }
        if (network == "kcp") {
            if (form.advancedValue("headerType") !in setOf("", "none") || form.advancedValue("seed").isNotBlank()) {
                return NodeFormIssue("mKCP header / seed", NodeFormIssueKind.LEGACY_KCP)
            }
            for (key in NodeFormOptions.kcpKeys) {
                val value = form.advancedValue(key)
                if (value.isBlank()) continue
                val number = value.toLongOrNull() ?: return invalid(key)
                val range = when (key) {
                    "mtu" -> 21L..4294967295L
                    "tti" -> 10L..1000L
                    "cwndMultiplier" -> 1L..4294967295L
                    "maxSendingWindow" -> (form.advancedValue("mtu").toLongOrNull() ?: 1350L)..4294967295L
                    else -> 0L..4294967295L
                }
                if (number !in range) return invalid(key)
            }
        }
        if (protocol == Protocol.VLESS) {
            if (form.vlessEncryption.isBlank()) return required("encryption")
            if (!validVlessEncryption(form.vlessEncryption)) return invalid("encryption")
            if (form.flow !in setOf("", "xtls-rprx-vision", "xtls-rprx-vision-udp443")) return invalid("flow")
            if (form.flow.isNotBlank() && form.vlessEncryption == "none" &&
                (network != "raw" || form.transportSecurity !in setOf("tls", "reality"))) return incompatible("flow / network / security")
        }
        if (protocol == Protocol.VMESS && form.vmessSecurity !in setOf("auto", "aes-128-gcm", "chacha20-poly1305", "none", "zero")) return invalid("security")
        if (form.transportSecurity == "reality") {
            if (form.publicKey.isBlank()) return required("password (REALITY)")
            if (!Regex("[A-Za-z0-9_-]{43}").matches(form.publicKey) ||
                runCatching { Base64Compat.decode(form.publicKey.replace('-', '+').replace('_', '/')).size }.getOrNull() != 32) return invalid("password (REALITY)")
            if (form.fingerprint.isBlank()) return required("fingerprint")
            if (form.shortId.length > 16 || form.shortId.length % 2 != 0 || !form.shortId.all { it in "0123456789abcdefABCDEF" }) return invalid("shortId")
        }
        for (key in NodeFormOptions.advancedKeys(form)) {
            val value = form.advancedValue(key)
            val options = NodeFormOptions.advancedOptions(form, key)
            if (options != null && value !in options) return invalid(key)
            if (key == "spx" && value.isNotBlank() && !value.startsWith('/')) return invalid("spiderX")
            if (key == "pqv" && value.isNotBlank() && decodeUrlKeySize(value) != 1952) return invalid("mldsa65Verify")
            if (key == "extra" && value.isNotBlank() && runCatching { Json.parseToJsonElement(value) is JsonObject }.getOrDefault(false).not()) return invalid("xhttpSettings.extra")
        }
    }
    if (protocol == Protocol.HYSTERIA2 || (NodeFormOptions.hasTransport(protocol) && form.transportSecurity == "tls")) {
        val pins = form.advancedValue("pcs").split(',').map(String::trim).filter(String::isNotEmpty)
        if (pins.any { !Regex("[a-fA-F0-9]{64}").matches(it.replace(":", "")) }) return invalid("pinnedPeerCertSha256")
    }
    return null
}

private fun decodeUrlKeySize(value: String): Int? =
    if (value.any { it !in "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_-" }) null
    else runCatching { Base64Compat.decode(value.replace('-', '+').replace('_', '/')).size }.getOrNull()

private fun validVlessEncryption(value: String): Boolean {
    if (value == "none") return true
    val parts = value.split('.')
    return parts.size >= 4 && parts[0] == "mlkem768x25519plus" &&
        parts[1] in setOf("native", "xorpub", "random") && parts[2] in setOf("0rtt", "1rtt") &&
        parts.drop(3).all { it.length < 20 || decodeUrlKeySize(it) in setOf(32, 1184) }
}
