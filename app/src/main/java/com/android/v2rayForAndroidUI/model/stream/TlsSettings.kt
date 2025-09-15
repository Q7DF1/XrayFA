package com.android.v2rayForAndroidUI.model.stream

data class TlsSettings(
    val serverName: String? = null,
    val fingerprint: String? = null,
    val allowInsecure: Boolean = false
)