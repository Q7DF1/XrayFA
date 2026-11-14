package com.android.xrayfa.config

import com.android.xrayfa.model.MuxObject
import com.android.xrayfa.model.OutboundObject
import com.android.xrayfa.model.VLESSOutboundConfigurationObject
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Local node option; deliberately excluded from share links. */
object NodeMuxConfig {
    private val json = Json { encodeDefaults = true }

    fun decode(value: String): MuxObject = json.decodeFromString<MuxObject>(value).also(::validate)
    fun encode(value: MuxObject): String = json.encodeToString(value.also(::validate))

    fun validate(value: MuxObject) {
        require(value.enabled) { "Mux must be enabled when configured" }
        require(value.concurrency in -1..128) { "Invalid Mux TCP concurrency" }
        require(value.xudpConcurrency in -1..1024) { "Invalid XUDP concurrency" }
        require(value.xudpProxyUDP443 in setOf("reject", "allow", "skip")) { "Invalid Mux UDP/443 policy" }
    }
}

/** Shared by runtime, delay tests, and the outbound preview. */
fun <T : com.android.xrayfa.model.AbsOutboundConfigurationObject> withNodeMux(
    outbound: OutboundObject<T>, settings: String?,
): OutboundObject<T> {
    if (settings == null) return outbound
    // Subscription updates can change the transport while retaining a local override.
    // Keep that override dormant when the refreshed node cannot use ordinary Mux.
    if (outbound.protocol !in setOf("vmess", "vless", "trojan", "shadowsocks", "socks", "http") ||
        outbound.streamSettings?.network in setOf("xhttp", "splithttp")) return outbound.copy(mux = null)
    val configured = NodeMuxConfig.decode(settings)
    val vision = (outbound.settings as? VLESSOutboundConfigurationObject)?.vnext
        ?.any { server -> server.users.any { !it.flow.isNullOrEmpty() } } == true
    return outbound.copy(mux = if (vision) configured.copy(concurrency = -1) else configured)
}
