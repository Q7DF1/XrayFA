package com.android.xrayfa.shared.config

import com.android.xrayfa.config.NodeMuxConfig
import com.android.xrayfa.model.MuxObject
import com.android.xrayfa.model.protocol.Protocol

data class NodeMuxForm(
    val concurrency: String = "8",
    val xudpConcurrency: String = "8",
    val udp443: String = "reject",
) {
    fun toConfig() = MuxObject(enabled = true, concurrency = concurrency.toInt(),
        xudpConcurrency = xudpConcurrency.toInt(), xudpProxyUDP443 = udp443)

    companion object {
        fun fromSettings(value: String): NodeMuxForm {
            val config = NodeMuxConfig.decode(value)
            return NodeMuxForm(config.concurrency.toString(), config.xudpConcurrency.toString(), config.xudpProxyUDP443)
        }
    }
}

fun NodeEditForm.supportsMux(): Boolean = selectedProtocol != Protocol.HYSTERIA2 &&
    (!NodeFormOptions.hasTransport(selectedProtocol) || NodeFormOptions.canonicalNetwork(network) != "xhttp")

fun NodeEditForm.visionMux(): Boolean = selectedProtocol == Protocol.VLESS && flow.isNotEmpty()

fun NodeEditForm.muxIssue(): String? {
    val value = mux ?: return null
    if (!supportsMux()) return "mux / protocol / network"
    val tcp = if (visionMux()) -1 else value.concurrency.toIntOrNull()
    if (tcp == null || tcp !in -1..128 || (network == "xdrive" && tcp < 0)) return "mux.concurrency"
    val udp = value.xudpConcurrency.toIntOrNull()
    if (udp == null || udp !in -1..1024 || (network == "xdrive" && udp < 0)) return "mux.xudpConcurrency"
    if (value.udp443 !in setOf("reject", "allow", "skip") || (network == "xdrive" && value.udp443 == "skip")) return "mux.xudpProxyUDP443"
    return null
}

fun NodeEditForm.muxSettings(): String? {
    require(muxIssue() == null) { "Invalid node Mux configuration" }
    return mux?.let {
        NodeMuxConfig.encode(it.copy(concurrency = if (visionMux()) "-1" else it.concurrency).toConfig())
    }
}
