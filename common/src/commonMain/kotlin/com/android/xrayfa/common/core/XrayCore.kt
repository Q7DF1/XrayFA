package com.android.xrayfa.common.core

/**
 * Platform abstraction for Xray-core lifecycle and diagnostics.
 *
 * Android actual: [com.android.xrayfa.core.XrayCoreManager] (libv2ray).
 * iOS actual: in-app LibXrayLite wrapper for delay diagnostics (VPN loop stays in PacketTunnel).
 */
interface XrayCore : TrafficDetector {

    suspend fun startXrayCore(startOptions: CoreStartOptions, tunFd: Int?): Boolean

    fun stopXrayCore()

    /** Measures delay against the currently running core; returns -1 when not running or on error. */
    fun measureDelaySync(url: String): Long

    /** Measures outbound delay for a config JSON without starting the VPN; delegates to native core. */
    fun measureOutboundDelay(config: String, url: String): Long
}
