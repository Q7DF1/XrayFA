package com.android.xrayfa.shared.platform

import com.android.xrayfa.common.core.CoreStartOptions
import com.android.xrayfa.common.core.XrayAssetPaths
import com.android.xrayfa.common.core.XrayCore
import com.android.xrayfa.nativebridge.NativeBridgeFactory
import com.android.xrayfa.nativebridge.XrayBridge
import com.android.xrayfa.nativebridge.XrayCoreCallback
import com.android.xrayfa.nativebridge.XrayCoreController
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * In-app Xray-core for diagnostics. VPN start/stop stays in PacketTunnel;
 * [measureOutboundDelay] uses the Step 100 ObjC shim.
 */
class IosXrayCore(
    assetPaths: XrayAssetPaths,
    private val xrayBridge: XrayBridge = NativeBridgeFactory.createXrayBridge(),
) : XrayCore {
    private val controller: XrayCoreController
    private val _trafficFlow = MutableSharedFlow<Pair<Double, Double>>(replay = 1)
    override val trafficFlow: SharedFlow<Pair<Double, Double>> = _trafficFlow.asSharedFlow()

    init {
        xrayBridge.initCoreEnv(assetPaths.basePath, IOS_XUDP_DEVICE_ID)
        controller = xrayBridge.newCoreController(IosNoopCoreCallback)
    }

    override suspend fun startXrayCore(
        startOptions: CoreStartOptions,
        tunFd: Int?,
    ): Boolean = false

    override fun stopXrayCore() = Unit

    override fun measureDelaySync(url: String): Long {
        if (!controller.isRunning) return -1L
        return try {
            controller.measureDelay(url)
        } catch (_: Exception) {
            -1L
        }
    }

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

private object IosNoopCoreCallback : XrayCoreCallback {
    override fun onStartup(): Long = 0L

    override fun onShutdown(): Long = 0L

    override fun onEmitStatus(
        code: Long,
        message: String?,
    ): Long = 0L
}
