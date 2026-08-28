package com.android.xrayfa.nativebridge

import libv2ray.CoreController

internal class Libv2rayCoreControllerAdapter(
    private val controller: CoreController,
) : XrayCoreController {
    override val isRunning: Boolean
        get() = controller.isRunning

    override fun startLoop(configJson: String, tunFd: Int) {
        controller.startLoop(configJson, tunFd)
    }

    override fun stopLoop() {
        controller.stopLoop()
    }

    override fun measureDelay(url: String): Long = controller.measureDelay(url)

    override fun queryAllOutboundTrafficStats(): String =
        controller.queryAllOutboundTrafficStats()
}
