package com.android.xrayfa.nativebridge

/**
 * Android actual factory. Xray uses libv2ray JNI; Tun uses hev-socks5-tunnel JNI.
 */
actual object NativeBridgeFactory {
    actual fun createXrayBridge(): XrayBridge = Libv2rayXrayBridge()

    actual fun createTunBridge(): TunBridge = HevTunBridge()
}
