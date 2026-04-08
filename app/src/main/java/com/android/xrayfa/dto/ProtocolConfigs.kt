package com.android.xrayfa.dto

import com.android.xrayfa.model.protocol.Protocol
import com.google.gson.JsonObject

data class VLESSConfig(
    val protocol: Protocol = Protocol.VLESS,
    val remark: String? = null,
    val uuid: String,
    val server: String,
    val port: Int,
    val param: Map<String, String>
)

data class VMESSConfig(
    val protocol: Protocol = Protocol.VMESS,
    val uuid: String,
    val tls: String,
    val host: String,
    val network: String,
    val address: String,
    val others: JsonObject
)

data class ShadowSocksConfig(
    val method: String,
    val password: String,
    val server: String,
    val port: Int,
    val tag: String?
)

data class TrojanConfig(
    val scheme: String,
    val password: String,
    val host: String?,
    val port: Int?,
    val params: Map<String, String>,
    val remark: String?,
    val original: String
)

data class Hysteria2Config(
    val remark: String?,
    val address: String,
    val port: Int,
    val version:Int = 2,
    val auth: String,
    val param: Map<String, String>
)
