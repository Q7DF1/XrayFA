package com.android.xrayfa.nativebridge

/**
 * Lifecycle callbacks from the native Xray-core controller.
 *
 * Mirrors libv2ray [CoreCallbackHandler] so Android actual can delegate 1:1 in a later step.
 */
interface XrayCoreCallback {
    fun onStartup(): Long
    fun onShutdown(): Long
    fun onEmitStatus(code: Long, message: String?): Long
}
