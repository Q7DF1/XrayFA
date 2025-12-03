package com.android.xrayfa.model

import com.android.xrayfa.model.stream.StreamSettingsObject
import kotlinx.serialization.Serializable

data class InboundObject(
    val listen: String? = null,
    val port: Int? = null,
    val protocol: String,
    val settings: AbsInboundConfigurationObject? = null,
    val streamSettings: StreamSettingsObject? = null,
    val tag: String? = null,
    val sniffing: SniffingObject? = null,
    val allocate: AllocateObject? = null
)

@Serializable
data class SniffingObject(
    val enabled: Boolean = false,
    val destOverride: List<String> = emptyList(),
    val metadataOnly: Boolean = false,
    val routeOnly: Boolean = false
)

@Serializable
data class AllocateObject(
    val strategy: String? = null,
    val refresh: Int? = null,
    val concurrency: Int? = null
)

/**
 * 协议配置对象
 */
abstract class AbsInboundConfigurationObject {

}

data class VLESSInboundConfigurationObject(
    val clients: List<ClientObject>? = null,
    val description: String? = null,
    val fallbacks: List<FallbackObject>
): AbsInboundConfigurationObject()

data class SocksInboundConfigurationObject(
    val auth: String? = null,
    val userLevel: Int? = null,
    val udp: Boolean? = null,
    val ip: String? = null,
    //val accounts: AcctountObject? = null,
): AbsInboundConfigurationObject()

data class TunnelInboundConfigurationObject( //dokodemo-door
    val address: String? = null,
    val port: Int? = null,
    val portMap:Map<String,String>? = null,
    val network: String? = null,
    val followRedirect: Boolean? = null,
    val userLevel: Int? = null,
): AbsInboundConfigurationObject()


data class ClientObject(
    val id: String,
    val level: Int? = null,
    val email: String? = null,
    val flow: String? = null,
)

data class FallbackObject(
    val name: String? = null,
    val alpn: String? = null,
    val path: String? = null,
    val dest: Int? = null,
    val xver: Int? = null,
)





data class HappyEyeballs(
    val tryDelayMs: Int = 250,
    val prioritizeIPv6: Boolean = false,
    val interleave: Int = 1,
    val maxConcurrent: Int = 1,
)

data class Sockopt(
    val mark: Int = 0,
    val tcpMaxSeg: Int? = null,
    val tcpFastOpen: Any? = null, // 可为 Boolean 或 Int
    val tproxy: String = "off",
    val domainStrategy: String = "AsIs",
    val happyEyeballs: HappyEyeballs? = null,
    val dialerProxy: String = "",
    val acceptProxyProtocol: Boolean = false,
    val tcpKeepAliveInterval: Int = 0,
    val tcpKeepAliveIdle: Int = 300,
    val tcpUserTimeout: Int = 10000,
    val tcpCongestion: String = "bbr",
    val interfaceName: String = "", // “interface”为 Kotlin 保留关键字，用 interfaceName 替代
    val v6only: Boolean = false,
    val tcpWindowClamp: Int = 600,
    val tcpMptcp: Boolean = false,
    val addressPortStrategy: String = "",
    //val customSockopt: Any? = null,
)


/**
 * Port can be:
 * - Integer (e.g. 1080)
 * - String (e.g. "1234", "5-10", "11,13,15-17", or "env:PORT")
 * We'll represent it as a String and allow parsing logic later.
 */
typealias Port = String
