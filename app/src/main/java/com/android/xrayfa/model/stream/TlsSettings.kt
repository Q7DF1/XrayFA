package com.android.xrayfa.model.stream

data class TlsSettings(
    val serverName: String? = null,
    val fingerprint: String? = null,
    val allowInsecure: Boolean = false
)