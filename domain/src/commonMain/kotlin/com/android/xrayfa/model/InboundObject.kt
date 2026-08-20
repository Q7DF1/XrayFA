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
    val allocate: AllocateObject? = null,
)

@Serializable
data class SniffingObject(
    val enabled: Boolean = false,
    val destOverride: List<String> = emptyList(),
    val metadataOnly: Boolean = false,
    val domainsExcluded: List<String>? = null,
    val routeOnly: Boolean = false,
)

@Serializable
data class AllocateObject(
    val strategy: String? = null,
    val refresh: Int? = null,
    val concurrency: Int? = null,
)

abstract class AbsInboundConfigurationObject

@Serializable
data class VLESSInboundConfigurationObject(
    val clients: List<ClientObject>? = null,
    val decryption: String = "none",
    val fallbacks: List<FallbackObject>? = null,
) : AbsInboundConfigurationObject()

@Serializable
data class SocksInboundConfigurationObject(
    val auth: String? = null,
    val accounts: List<AccountObject>? = null,
    val userLevel: Int? = null,
    val udp: Boolean? = null,
    val ip: String? = null,
) : AbsInboundConfigurationObject() {

    @Serializable
    data class AccountObject(
        val user: String,
        val pass: String,
    )
}

@Serializable
data class HttpInboundConfigurationObject(
    val timeout: Int? = null,
    val userLevel: Int? = null,
) : AbsInboundConfigurationObject()

@Serializable
data class TunnelInboundConfigurationObject(
    val address: String? = null,
    val port: Int? = null,
    val portMap: Map<String, String>? = null,
    val network: String? = null,
    val followRedirect: Boolean? = null,
    val userLevel: Int? = null,
) : AbsInboundConfigurationObject()

@Serializable
data class TunInboundConfigurationObject(
    val name: String?,
    val MTU: Int?,
    val userLevel: Int?,
) : AbsInboundConfigurationObject()

@Serializable
data class WireGuardInboundConfigurationObject(
    val secretKey: String,
    val peers: List<WireGuardInboundPeer>,
    val mtu: Int = 1420,
    val kernelMode: Boolean = false,
) : AbsInboundConfigurationObject()

@Serializable
data class WireGuardInboundPeer(
    val publicKey: String,
    val allowedIPs: List<String>,
)

@Serializable
data class ClientObject(
    val id: String,
    val level: Int? = null,
    val email: String? = null,
    val flow: String? = null,
)

@Serializable
data class FallbackObject(
    val name: String? = null,
    val alpn: String? = null,
    val path: String? = null,
    val dest: String? = null,
    val xver: Int? = null,
)

typealias Port = String
