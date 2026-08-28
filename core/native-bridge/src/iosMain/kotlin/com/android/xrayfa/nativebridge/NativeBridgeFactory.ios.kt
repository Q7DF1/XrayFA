package com.android.xrayfa.nativebridge

/**
 * iOS TunBridge stub — tun2socks runs in PacketTunnel via HevSocks5Tunnel (E.5e), not KMP.
 * Xray-core in NE uses LibXrayLite Swift API; KMP [TunBridge] stays unused on iOS.
 */
private class IosStubTunBridge : TunBridge {
    override fun startTun2Socks(configPath: String, tunFd: Int): Boolean = false

    override fun stopTun2Socks(): Boolean = false

    override fun isRunning(): Boolean = false
}

actual object NativeBridgeFactory {
    actual fun createXrayBridge(): XrayBridge = Libv2rayXrayBridge()

    actual fun createTunBridge(): TunBridge = IosStubTunBridge()
}
