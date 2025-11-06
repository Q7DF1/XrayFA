package com.android.xrayfa.model

data class LogObject(
    val access: String? = null,
    val error: String? = null,
    val logLevel: String? = null,
    val dnsLog: Boolean? = null,
    val maskAddress: String? = null
)
