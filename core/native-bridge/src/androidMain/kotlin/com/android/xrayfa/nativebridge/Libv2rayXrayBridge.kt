package com.android.xrayfa.nativebridge

import libv2ray.Libv2ray

/** Android actual: thin delegate over gomobile libv2ray JNI. */
internal class Libv2rayXrayBridge : XrayBridge {
    override fun initCoreEnv(basePath: String, deviceId: String) {
        Libv2ray.initCoreEnv(basePath, deviceId)
    }

    override fun newCoreController(callback: XrayCoreCallback): XrayCoreController {
        val handler = Libv2rayCoreCallbackAdapter(callback)
        return Libv2rayCoreControllerAdapter(Libv2ray.newCoreController(handler))
    }

    override fun checkVersion(): String = Libv2ray.checkVersionX()

    override fun measureOutboundDelay(configJson: String, url: String): Long =
        Libv2ray.measureOutboundDelay(configJson, url)
}
