package com.android.xrayfa.core

import com.android.xrayfa.common.routing.RoutingMode

/** Metadata captured from the configuration that actually started the VPN service. */
data class ActiveVpnSession(
    val sessionId: Long,
    val nodeId: Int,
    val nodeName: String?,
    val serverAddress: String?,
    val serverPort: Int?,
    val routingMode: RoutingMode?,
    val customConfiguration: Boolean,
    val socksInboundAvailable: Boolean,
)
