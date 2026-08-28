package com.android.xrayfa.core

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.annotation.StringDef
import com.android.xrayfa.R
import com.android.xrayfa.common.core.CoreStartOptions
import com.android.xrayfa.common.core.TrafficDetector
import com.android.xrayfa.common.core.XrayAssetPaths
import com.android.xrayfa.common.core.XrayCore
import com.android.xrayfa.datastore.SettingsRepository
import com.android.xrayfa.nativebridge.XrayBridge
import com.android.xrayfa.nativebridge.XrayCoreCallback
import com.android.xrayfa.nativebridge.XrayCoreController
import com.android.xrayfa.nativebridge.outboundTrafficValue
import com.android.xrayfa.parser.ParserFactory
import com.android.xrayfa.utils.Device
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val TAG_PROXY = "proxy"
const val TAG_DIRECT = "direct"
@StringDef(value = [
    TAG_PROXY,
    TAG_DIRECT
])
@Retention(AnnotationRetention.SOURCE)
annotation class Tag

const val UP_STEAM = "uplink"
const val DOWN_STEAM = "downlink"
@StringDef(value =[
    UP_STEAM,
    DOWN_STEAM
])
annotation class Stream

class XrayCoreManager(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    private val parserFactory: ParserFactory,
    private val settingsRepository: SettingsRepository,
    private val assetPaths: XrayAssetPaths,
    private val xrayBridge: XrayBridge,
): XrayCore {

    companion object {
        const val TAG = "XrayCoreManager"
    }
    private var coreController: XrayCoreController? = null
    private var job: Job? = null

    private val _trafficFlow = MutableSharedFlow<Pair<Double, Double>>(replay = 1)
    override val trafficFlow: SharedFlow<Pair<Double, Double>> = _trafficFlow.asSharedFlow()

    private val controllerCallback = object : XrayCoreCallback {
        override fun onEmitStatus(code: Long, message: String?): Long {
            Log.i(TAG, "onEmitStatus: $code $message")
            return 0L
        }

        override fun onShutdown(): Long {
            Log.i(TAG, "shutdown: end")
            return 0L
        }

        override fun onStartup(): Long {
            Log.i(TAG, "startup: start")
            return 0L
        }
    }

    init {
        Log.i(TAG, assetPaths.basePath)
        xrayBridge.initCoreEnv(
            assetPaths.basePath, Device.getDeviceIdForXUDPBaseKey()
        )
        coroutineScope.launch {
            val xrayCoreVersion = xrayBridge.checkVersion()
            if (settingsRepository.settingsFlow.first().xrayCoreVersion != xrayCoreVersion) {
                settingsRepository.setXrayCoreVersion(xrayCoreVersion)
            }
        }
        coreController = xrayBridge.newCoreController(controllerCallback)
    }


    override fun measureDelaySync(url: String): Long {
        if (coreController?.isRunning == false) return -1
        return try {
            coreController?.measureDelay(url) ?: 0L
        } catch (e: Exception) {
            Log.e(TAG, "measureDelaySync: ${e.message}")
            -1
        }
    }

    override fun measureOutboundDelay(config: String, url: String): Long {
        return xrayBridge.measureOutboundDelay(config, url)
    }

    override suspend fun startXrayCore(startOptions: CoreStartOptions, tunFd: Int?): Boolean {
        try {
            tunFd?.let {
                coreController?.startLoop(parserFactory.getParser(startOptions.url).parse(startOptions), tunFd)
            }
            // Start traffic detection after core is confirmed running
            startTrafficDetection()
            return true
        }catch (e: Exception) {
            Log.e(TAG, "startXrayCore failed: ${e.message}")
            withContext(Dispatchers.Main) {
                Toast.makeText(context,R.string.core_start_failed, Toast.LENGTH_SHORT).show()
            }

            return false
        }
    }

    override fun stopXrayCore() {
        stopTrafficDetection()
        coreController?.stopLoop()
    }

    override fun startTrafficDetection() {
        job?.cancel()
        job = coroutineScope.launch(Dispatchers.IO) {
            var last = System.currentTimeMillis()
            // send initial zero values
            _trafficFlow.emit(Pair(0.0, 0.0))
            delay(3000L)
            while (true) {
                val cur = System.currentTimeMillis()
                val snapshot = coreController?.queryAllOutboundTrafficStats().orEmpty()
                val up = outboundTrafficValue(snapshot, TAG_PROXY, UP_STEAM)
                val down = outboundTrafficValue(snapshot, TAG_PROXY, DOWN_STEAM)
                val deltaTimeSec = (cur - last) / 1000.0
                val upSpeed = if (deltaTimeSec > 0) (up / deltaTimeSec) / 1024 else 0.0
                val downSpeed = if (deltaTimeSec > 0) (down / deltaTimeSec) / 1024 else 0.0
                _trafficFlow.emit(Pair(upSpeed, downSpeed))
                last = cur
                delay(3000L)
            }
        }
    }

    override fun stopTrafficDetection() {
        job?.cancel()
        Log.d(TAG, "stopTrafficDetection: ${job?.isActive}")
    }
}
