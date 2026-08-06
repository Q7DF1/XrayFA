package com.android.xrayfa.nativebridge

/**
 * iOS compile-only stub until gomobile xcframework is linked (E.2+).
 */
private class IosStubXrayCoreController : XrayCoreController {
    override val isRunning: Boolean = false

    override fun startLoop(configJson: String, tunFd: Int) = Unit

    override fun stopLoop() = Unit

    override fun measureDelay(url: String): Long = -1L

    override fun queryStats(tag: String, stream: String): Long = 0L
}

private class IosStubXrayBridge : XrayBridge {
    override fun initCoreEnv(basePath: String, deviceId: String) = Unit

    override fun newCoreController(callback: XrayCoreCallback): XrayCoreController =
        IosStubXrayCoreController()

    override fun checkVersion(): String = "ios-stub"

    override fun measureOutboundDelay(configJson: String, url: String): Long = -1L
}

private class IosStubTunBridge : TunBridge {
    override fun startTun2Socks(configPath: String, tunFd: Int): Boolean = false

    override fun stopTun2Socks(): Boolean = false

    override fun isRunning(): Boolean = false
}

actual object NativeBridgeFactory {
    actual fun createXrayBridge(): XrayBridge = IosStubXrayBridge()

    actual fun createTunBridge(): TunBridge = IosStubTunBridge()
}
