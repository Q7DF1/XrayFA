package com.android.xrayfa.nativebridge

/**
 * Handle to a running Xray-core loop.
 *
 * Mirrors libv2ray [CoreController]; Android actual wraps JNI in a later step.
 */
interface XrayCoreController {
    val isRunning: Boolean
    fun startLoop(configJson: String, tunFd: Int)
    fun stopLoop()
    fun measureDelay(url: String): Long
    fun queryStats(tag: String, stream: String): Long
}
