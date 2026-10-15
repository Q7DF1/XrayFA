package com.android.xrayfa.model.stream

import kotlinx.serialization.Serializable

/** Fields consumed by KCPConfig.Build in the pinned Xray-core. Null uses core defaults. */
@Serializable
data class KcpSettings(
    val mtu: Long? = null,
    val tti: Long? = null,
    val uplinkCapacity: Long? = null,
    val downlinkCapacity: Long? = null,
    val cwndMultiplier: Long? = null,
    val maxSendingWindow: Long? = null,
)
