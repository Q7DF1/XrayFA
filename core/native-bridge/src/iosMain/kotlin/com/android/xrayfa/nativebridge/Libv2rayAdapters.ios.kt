@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.android.xrayfa.nativebridge

import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import libv2ray.Libv2rayCoreCallbackHandler
import libv2ray.Libv2rayCoreController
import kotlinx.cinterop.LongVarOf

internal class Libv2rayCoreCallbackAdapter(
    private val callback: XrayCoreCallback,
) : Libv2rayCoreCallbackHandler() {
    override fun onEmitStatus(
        p0: Long,
        p1: String?,
    ): Long = callback.onEmitStatus(p0, p1)

    override fun shutdown(): Long = callback.onShutdown()

    override fun startup(): Long = callback.onStartup()
}

internal class Libv2rayCoreControllerAdapter(
    private val controller: Libv2rayCoreController,
) : XrayCoreController {
    override val isRunning: Boolean
        get() = controller.isRunning()

    override fun startLoop(
        configJson: String,
        tunFd: Int,
    ) {
        memScoped {
            val error = alloc<ObjCObjectVar<platform.Foundation.NSError?>>()
            controller.startLoop(configJson, tunFd, error.ptr)
        }
    }

    override fun stopLoop() {
        memScoped {
            val error = alloc<ObjCObjectVar<platform.Foundation.NSError?>>()
            controller.stopLoop(error.ptr)
        }
    }

    override fun measureDelay(url: String): Long =
        memScoped {
            val result = alloc<LongVarOf<Long>>()
            val error = alloc<ObjCObjectVar<platform.Foundation.NSError?>>()
            val ok = controller.measureDelay(url, result.ptr, error.ptr)
            if (!ok) {
                -1L
            } else {
                result.value
            }
        }

    override fun queryStats(
        tag: String,
        stream: String,
    ): Long = controller.queryStats(tag, stream)
}
