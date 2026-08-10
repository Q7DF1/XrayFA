package com.android.xrayfa.shared.config

import com.android.xrayfa.model.protocol.Protocol

/** Form field snapshot for shared [SharedEditScreen]. */
data class NodeEditForm(
    val selectedProtocol: Protocol = Protocol.VLESS,
    val remarks: String = "",
    val address: String = "",
    val port: String = "",
    val uuidOrPassword: String = "",
    val username: String = "",
    val flow: String = "",
    val vlessEncryption: String = "none",
    val vmessSecurity: String = "auto",
    val ssMethod: String = "aes-256-gcm",
    val network: String = "tcp",
    val transportSecurity: String = "none",
    val wsPath: String = "/",
    val wsHost: String = "",
    val grpcServiceName: String = "",
    val sni: String = "",
    val fingerprint: String = "chrome",
    val publicKey: String = "",
    val shortId: String = "",
    val hysteria2Obfs: String = "",
    val hysteria2ObfsPassword: String = "",
    val hysteria2Alpn: String = "",
    val allowInsecure: Boolean = false,
)
