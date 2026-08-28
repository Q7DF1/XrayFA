package com.android.xrayfa.nativebridge

import libv2ray.CoreCallbackHandler

internal class Libv2rayCoreCallbackAdapter(
    private val callback: XrayCoreCallback,
) : CoreCallbackHandler {
    override fun onEmitStatus(code: Long, message: String?): Long =
        callback.onEmitStatus(code, message)

    override fun shutdown(): Long = callback.onShutdown()

    override fun startup(): Long = callback.onStartup()
}
