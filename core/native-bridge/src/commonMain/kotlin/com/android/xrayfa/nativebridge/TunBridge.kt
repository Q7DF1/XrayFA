package com.android.xrayfa.nativebridge

/**
 * Low-level tun2socks native bridge.
 *
 * Android actual will wrap hev-socks5-tunnel JNI; iOS actual will use packet-based API later.
 */
interface TunBridge {
    fun startTun2Socks(configPath: String, tunFd: Int): Boolean
    fun stopTun2Socks(): Boolean
    fun isRunning(): Boolean
}
