package com.android.xrayfa.model.stream

import kotlinx.serialization.Serializable

@Serializable
data class WsSettings(
    val path: String = "/",
    val host: String = "",
    val headers: Map<String, String>? = null,
    val heartbeatPeriod: Int = 0,
)
