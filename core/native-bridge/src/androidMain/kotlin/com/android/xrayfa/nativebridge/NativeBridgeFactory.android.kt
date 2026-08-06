package com.android.xrayfa.nativebridge

/**
 * Android actual factory. Xray uses libv2ray JNI; Tun stays placeholder until E.1c.
 */
actual object NativeBridgeFactory {
    actual fun createXrayBridge(): XrayBridge = Libv2rayXrayBridge()

    actual fun createTunBridge(): TunBridge = UnimplementedTunBridge()
}

private class UnimplementedTunBridge : TunBridge {
    override fun startTun2Socks(configPath: String, tunFd: Int): Boolean = false

    override fun stopTun2Socks(): Boolean = false

    override fun isRunning(): Boolean = false
}
