package com.android.xrayfa.model.stream

data class KcpSettings(
    val mtu: Int = 1350,
    val tti: Int = 20,
    val uplinkCapacity: Int = 5,
    val downlinkCapacity: Int = 20,
    val congestion: Boolean = false,
    val readBufferSize: Int = 1,
    val writeBufferSize: Int = 1,
    val header: KcpHeaderObject,
    val seed: String? = null
)

data class KcpHeaderObject(
    val type: String? = null,
    val domain: String? = null
)
