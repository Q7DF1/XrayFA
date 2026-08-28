package com.android.xrayfa.shared.platform

import com.android.xrayfa.common.core.CoreStartOptions
import com.android.xrayfa.common.core.XrayAssetPaths
import com.android.xrayfa.common.core.XrayCore
import com.android.xrayfa.nativebridge.NativeBridgeFactory
import com.android.xrayfa.nativebridge.XrayBridge
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Host-process diagnostics only. VPN start/stop stays in PacketTunnel.
 * Do not construct a gomobile CoreController here: KN/Swift subclasses of
 * Libv2rayCoreCallbackHandler crash with go_seq_go_to_refnum.
 */
class IosXrayCore(
    assetPaths: XrayAssetPaths,
    private val xrayBridge: XrayBridge = NativeBridgeFactory.createXrayBridge(),
) : XrayCore {
    private val _trafficFlow = MutableSharedFlow<Pair<Double, Double>>(replay = 1)
    override val trafficFlow: SharedFlow<Pair<Double, Double>> = _trafficFlow.asSharedFlow()

    init {
        xrayBridge.initCoreEnv(assetPaths.basePath, IOS_XUDP_DEVICE_ID)
    }

    override suspend fun startXrayCore(
        startOptions: CoreStartOptions,
        tunFd: Int?,
    ): Boolean = false

    override fun stopXrayCore() = Unit

    override fun measureDelaySync(url: String): Long = -1L

    override fun measureOutboundDelay(
        config: String,
        url: String,
    ): Long = xrayBridge.measureOutboundDelay(config, url)

    override fun startTrafficDetection() = Unit

    override fun stopTrafficDetection() = Unit

    private companion object {
        const val IOS_XUDP_DEVICE_ID = "ios-device"
    }
}
