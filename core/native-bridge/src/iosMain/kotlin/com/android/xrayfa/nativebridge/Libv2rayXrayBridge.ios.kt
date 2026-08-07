@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.android.xrayfa.nativebridge

import libv2ray.Libv2rayCheckVersionX
import libv2ray.Libv2rayInitCoreEnv
import libv2ray.Libv2rayNewCoreController

/** iOS actual: thin delegate over gomobile LibXrayLite (ObjC cinterop). */
internal class Libv2rayXrayBridge : XrayBridge {
    override fun initCoreEnv(basePath: String, deviceId: String) {
        Libv2rayInitCoreEnv(basePath, deviceId)
    }

    override fun newCoreController(callback: XrayCoreCallback): XrayCoreController {
        val handler = Libv2rayCoreCallbackAdapter(callback)
        val controller = Libv2rayNewCoreController(handler)
            ?: error("Libv2rayNewCoreController returned null")
        return Libv2rayCoreControllerAdapter(controller)
    }

    override fun checkVersion(): String = Libv2rayCheckVersionX()

    override fun measureOutboundDelay(configJson: String, url: String): Long {
        // gomobile's Libv2rayMeasureOutboundDelay is not callable from Kotlin cinterop (int64_t* out-param).
        // Matches prior iOS stub (-1L); use CoreController.measureDelay after startLoop when wired in NE (E.5d).
        return -1L
    }
}
