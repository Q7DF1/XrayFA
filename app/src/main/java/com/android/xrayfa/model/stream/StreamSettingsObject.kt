package com.android.xrayfa.model.stream
import com.android.xrayfa.model.Sockopt

data class StreamSettingsObject(
    val network: String = "raw",
    val security: String = "none",
    val tlsSettings: TlsSettings? = null,
    val realitySettings: RealitySettings? = null,
    val rawSettings: RawSettings? = null,
    val xhttpSettings: XHttpSettings? = null,
    val kcpSettings: KcpSettings? = null,
    val grpcSettings: GrpcSettings? = null,
    val wsSettings: WsSettings? = null,
    val httpUpgradeSettings: HttpUpgradeSettings? = null,
    val hysteriaSettings: HysteriaSettings? = null,
    val finalMask:FinalMask? =null,
    val sockopt: Sockopt? = null,

    @Deprecated("QUIC has been removed in Xray v24.9.7")
    val quicSettings: Any? = null,
    @Deprecated("DomainSocket has been removed in Xray v24.9.7")
    val dsSettings: Any? = null
)

data class XHttpSettings(
    val mode: String? = "splitHttp", // "splitHttp" | "packetStreaming"
    val host: String? = null,
    val path: String? = null,
    val extra: Map<String, String>? = null,
    val scMaxEachPostBytes: String? = null,
    val scMaxConcurrentPosts: String? = null,
    val scMinPostsIntervalMs: String? = null,
    val xmux: Map<String, Any>? = null // v24.9.30 新增
)

data class HttpUpgradeSettings(
    val acceptProxyProtocol: Boolean = false,
    val path: String = "/",
    val host: String = "",
    val headers: Map<String, String>? = null
)

data class HysteriaSettings(
    val version: Int = 2,
    val auth: String,
    val udpIdleTimeout: Int = 60,
    val masquerade: MasqueradeConfig = MasqueradeConfig()
)

data class MasqueradeConfig(
    val type: String = "",
    val dir: String = "",
    val url: String = "",
    val rewriteHost: Boolean = false,
    val insecure: Boolean = false,
    val content: String = "",
    val headers: Map<String, String> = mapOf("key" to "value"),
    val statusCode: Int = 0
)

data class FinalMask(
    val tcp: List<Any>? = null,
    val udp: List<Any>? = null,
    val quicParams: Any? = null
)
