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
    val fingerprint: String = "",
    val serverName: String = "",
    val publicKey: String = "",
    val allowInsecure: Boolean = false,
    val show: Boolean = false,
    val password: String = "",
    val shortId: String = "",
    val mldsa65Verify: String = "",
    val spiderX: String = ""
)