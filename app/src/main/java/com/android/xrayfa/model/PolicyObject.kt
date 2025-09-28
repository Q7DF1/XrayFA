package com.android.xrayfa.model

import kotlinx.serialization.Serializable

@Serializable
data class PolicyObject(
    val levels: Map<String, LevelPolicyObject>? = null,
    val system: SystemPolicyObject
)

@Serializable
data class LevelPolicyObject(
    val handshake: Int? = null,
    val connIdle: Int? = null,
    val uplinkOnly: Int? = null,
    val downlinkOnly: Int? = null,
    val statsUserUplink: Boolean? = null,
    val statsUserDownlink: Boolean? = null,
    val statsUserOnline: Boolean? = null,
    val bufferSize: Int? = null
)

@Serializable
data class SystemPolicyObject(
    val statsInboundUplink: Boolean? = null,
    val statsInboundDownlink: Boolean? = null,
    val statsOutboundUplink: Boolean? = null,
    val statsOutboundDownlink: Boolean? = null
)
