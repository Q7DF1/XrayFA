package com.android.v2rayForAndroidUI.model.stream
import com.android.v2rayForAndroidUI.model.Sockopt

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
    val sockopt: Sockopt? = null
)




class XHttpSettings()
class KcpSettings()

class HttpUpgradeSettings()
