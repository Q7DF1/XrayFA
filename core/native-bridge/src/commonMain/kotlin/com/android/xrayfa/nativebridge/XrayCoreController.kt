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

    /**
     * Snapshot of outbound counters from libv2ray `QueryAllOutboundTrafficStats`.
     * Format: `tag,direction,value;...`. Empty when stats are unavailable.
     * Each call resets the underlying counters.
     */
    fun queryAllOutboundTrafficStats(): String
}
