package com.android.xrayfa.model.stream

data class RealitySettings(
    val show: Boolean = false,
    val target: String? = null,
    val xver: Int? = null,
    val serverNames: List<String>? = null,
    val privateKey: String? = null,
    val minClientVer: String? = null,
    val maxClientVer: String? = null,
    val maxTimeDiff: Long? = null,
    val shortIds: List<String>? = null,
    val mldsa65Seed: String? = null,
    val limitFallbackUpload: LimitFallback? = null,
    val limitFallbackDownload: LimitFallback? = null,
    val fingerprint: String? = null,
    val serverName: String? = null,
    val publicKey: String?  = null,
    val allowInsecure: Boolean = false,
    val password: String? = null,
    val shortId: String? = null,
    val mldsa65Verify: String? = null,
    val spiderX: String? = null,
    val masterKeyLog: String? = null
)

data class LimitFallback(
    val afterBytes: Long = 0,
    val bytesPerSec: Long = 0,
    val burstBytesPerSec: Long = 0
)