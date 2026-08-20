package com.android.xrayfa.model

import com.android.xrayfa.model.stream.StreamSettingsObject
import kotlinx.serialization.Serializable

data class OutboundObject<T : AbsOutboundConfigurationObject>(
    val sendThrough: String = "0.0.0.0",
    val protocol: String? = null,
    var settings: T? = null,
    var tag: String,
    val streamSettings: StreamSettingsObject? = null,
    var proxySettings: ProxySettingsObject? = null,
    val mux: MuxObject? = null,
)

abstract class AbsOutboundConfigurationObject

@Serializable
class NoneOutboundConfigurationObject : AbsOutboundConfigurationObject()

@Serializable
data class VLESSOutboundConfigurationObject(
    val vnext: List<ServerObject>,
) : AbsOutboundConfigurationObject()

@Serializable
data class VMESSOutboundConfigurationObject(
    val vnext: List<ServerObject>,
) : AbsOutboundConfigurationObject()

@Serializable
data class TrojanOutboundConfigurationObject(
    val servers: List<TrojanServerObject>,
) : AbsOutboundConfigurationObject()

@Serializable
data class ShadowSocksOutboundConfigurationObject(
    val servers: List<ShadowSocksServerObject>,
) : AbsOutboundConfigurationObject()

@Serializable
data class WireGuardOutboundConfigurationObject(
    val secretKey: String,
    val address: List<String>,
    val peers: List<WireGuardOutboundPeer>,
    val mtu: Int = 1420,
    val reserved: List<Int>? = null,
    val workers: Int? = null,
    val domainStrategy: String? = "ForceIP",
) : AbsOutboundConfigurationObject() {

    @Serializable
    data class WireGuardOutboundPeer(
        val publicKey: String,
        val endpoint: String,
        val allowedIPs: List<String>? = null,
        val keepAlive: Int? = null,
    )
}

@Serializable
data class Hysteria2OutboundConfigurationObject(
    val version: Int = 2,
    val address: String,
    val port: Int,
) : AbsOutboundConfigurationObject()

@Serializable
data class SocksOutboundConfigurationObject(
    val servers: List<HttpSocksServerObject>,
) : AbsOutboundConfigurationObject()

@Serializable
data class HttpOutboundConfigurationObject(
    val servers: List<HttpSocksServerObject>,
) : AbsOutboundConfigurationObject()

@Serializable
data class ServerObject(
    val address: String,
    val port: Int,
    val users: List<UserObject>,
)

@Serializable
data class TrojanServerObject(
    val address: String?,
    val port: Int?,
    val password: String?,
    val email: String? = null,
    val level: Int? = null,
)

@Serializable
data class ShadowSocksServerObject(
    val email: String? = null,
    val address: String,
    val port: Int,
    val method: String,
    val password: String,
    val uot: Boolean = false,
    val UotVersion: Int? = null,
    val level: Int? = null,
    val ivCheck: Boolean? = null,
)

@Serializable
data class HttpSocksServerObject(
    val address: String,
    val port: Int,
    val users: List<HttpSocksUserObject>? = null,
)

@Serializable
data class HttpSocksUserObject(
    val user: String,
    val pass: String,
    val level: Int? = null,
)

@Serializable
data class UserObject(
    val id: String,
    val encryption: String? = "none",
    val flow: String? = null,
    val level: Int? = null,
    val security: String? = null,
)

@Serializable
data class ProxySettingsObject(
    var tag: String? = null,
)

@Serializable
data class MuxObject(
    val enable: Boolean = true,
    val concurrency: Int = 8,
    val xudpConcurrency: Int = 16,
    val xudpProxyUDP443: String = "reject",
)
