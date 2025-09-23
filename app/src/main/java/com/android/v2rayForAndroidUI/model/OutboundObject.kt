package com.android.v2rayForAndroidUI.model

import com.android.v2rayForAndroidUI.model.stream.StreamSettingsObject
import java.security.Security

data class OutboundObject(
    val sendThrough: String = "0.0.0.0",
    val protocol: String? = null,
    val settings: AbsOutboundConfigurationObject?,
    val tag: String,
    val streamSettings: StreamSettingsObject? = null,
    val proxySettings: ProxySettingsObject? = null,
    val mux: MuxObject? = null
)

abstract class AbsOutboundConfigurationObject{}
class NoneOutboundConfigurationObject: AbsOutboundConfigurationObject()
data class VLESSOutboundConfigurationObject(
    val vnext: List<ServerObject>
): AbsOutboundConfigurationObject()

data class VMESSOutboundConfigurationObject(
    val vnext: List<ServerObject>
): AbsOutboundConfigurationObject()

data class TrojanOutboundConfigurationObject(
    val servers: List<TrojanServerObject>
): AbsOutboundConfigurationObject()

data class ShadowSocksOutboundConfigurationObject(
    val services: List<ShadowSocksServerObject>
): AbsOutboundConfigurationObject()

data class ServerObject(
    val address: String,
    val port: Int,
    val users: List<UserObject>
)

data class TrojanServerObject(
    val address: String?,
    val port: Int?,
    val password: String?,
    val email: String? = null,
    val level: Int? = null
)

data class ShadowSocksServerObject(
    val email: String? = null,
    val address: String,
    val port:Int,
    val method: String,
    val password: String,
    val uot: Boolean = false,
    val UotVersion:Int? = null,
    val level: Int? = null
)

data class UserObject(
    val id: String, //uuid both
    val encryption: String? = "none", //vless
    val flow: String? = null, //vless
    val level: Int? = null, //both
    val security: String? = null //vmess
)

data class ProxySettingsObject(
    val tag: String? = null
)

data class MuxObject(
    val enable: Boolean = true,
    val concurrency: Int = 8,
    val xudpConcurrency: Int = 16,
    val xudpProxyUDP443: String = "reject"
)