package com.android.xrayfa.nativebridge

/**
 * Low-level Xray-core native bridge (gomobile / JNI).
 *
 * Higher-level VPN orchestration stays on [com.android.xrayfa.common.core.XrayCore] until E.2
 * rewires [com.android.xrayfa.core.XrayCoreManager] to delegate here.
 */
interface XrayBridge {
    fun initCoreEnv(basePath: String, deviceId: String)
    fun newCoreController(callback: XrayCoreCallback): XrayCoreController
    fun checkVersion(): String
    fun measureOutboundDelay(configJson: String, url: String): Long
}
