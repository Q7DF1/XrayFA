package com.android.xrayfa.nativebridge

/**
 * iOS TunBridge stub until packet-based tun2socks (Tun2socksKit / NEPacketFlow) lands in E.5d.
 * Xray-core wiring uses [Libv2rayXrayBridge] via gomobile LibXrayLite cinterop.
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
