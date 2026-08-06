package com.android.xrayfa.nativebridge

/**
 * Android placeholder until E.2 wires libv2ray JNI.
 * Not referenced by `:app` yet — zero runtime impact.
 */
private class UnimplementedXrayCoreController : XrayCoreController {
    override val isRunning: Boolean = false

    override fun startLoop(configJson: String, tunFd: Int) {
        error("XrayBridge Android actual not wired yet (E.2)")
    }

    override fun stopLoop() = Unit

    override fun measureDelay(url: String): Long = -1L

    override fun queryStats(tag: String, stream: String): Long = 0L
}

private class UnimplementedXrayBridge : XrayBridge {
    override fun initCoreEnv(basePath: String, deviceId: String) = Unit

    override fun newCoreController(callback: XrayCoreCallback): XrayCoreController =
        UnimplementedXrayCoreController()

    override fun checkVersion(): String = "unimplemented"

    override fun measureOutboundDelay(configJson: String, url: String): Long = -1L
}

private class UnimplementedTunBridge : TunBridge {
    override fun startTun2Socks(configPath: String, tunFd: Int): Boolean = false

    override fun stopTun2Socks(): Boolean = false

    override fun isRunning(): Boolean = false
}

actual object NativeBridgeFactory {
    actual fun createXrayBridge(): XrayBridge = UnimplementedXrayBridge()

    actual fun createTunBridge(): TunBridge = UnimplementedTunBridge()
}
