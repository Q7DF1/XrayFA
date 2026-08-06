package com.android.xrayfa.nativebridge

/** Platform entry point for native bridge implementations. */
expect object NativeBridgeFactory {
    fun createXrayBridge(): XrayBridge
    fun createTunBridge(): TunBridge
}
