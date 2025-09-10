package com.android.v2rayForAndroidUI.model.stream

data class RealitySettings(
//    val show: Boolean = false,
//    val target: String,
//    val xver: Int = 0,
//    val serverNames: List<String> = emptyList(),
//    val privateKey: String,
//    val minClientVer: String = "",
//    val maxClientVer: String = "",
//    val maxTimeDiff: Long = 0,
//    val shortIds: List<String> = emptyList(),
//    val mldsa65Seed: String = "",
//    val limitFallbackUpload: LimitFallback? = null,
//    val limitFallbackDownload: LimitFallback? = null,
    val fingerprint: String? = null,
    val serverName: String? = null,
    val publicKey: String?  = null,
    val allowInsecure: Boolean = false,
    val show: Boolean = false,
    val password: String? = null,
    val shortId: String = "",
    val mldsa65Verify: String? = null,
    val spiderX: String? = null
)