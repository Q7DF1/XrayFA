package com.android.xrayfa.nativebridge

import xrayfa.tun2socks.TProxyService

/** Android actual: thin delegate over hev-socks5-tunnel JNI (via [TProxyService] companion). */
internal class HevTunBridge : TunBridge {

    override fun startTun2Socks(configPath: String, tunFd: Int): Boolean {
        ensureNativeLoaded()
        return TProxyService.TProxyStartService(configPath, tunFd)
    }

    override fun stopTun2Socks(): Boolean {
        ensureNativeLoaded()
        return TProxyService.TProxyStopService()
    }

    override fun isRunning(): Boolean {
        ensureNativeLoaded()
        return TProxyService.TProxyIsRunning()
    }

    private fun ensureNativeLoaded() {
        // Companion init loads libhev-socks5-tunnel.so
        TProxyService::class.java
    }
}
