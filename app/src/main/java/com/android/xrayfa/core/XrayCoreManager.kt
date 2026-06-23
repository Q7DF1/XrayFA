package com.android.xrayfa.core

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.annotation.StringDef
import com.android.xrayfa.R
import com.android.xrayfa.common.di.qualifier.Application
import com.android.xrayfa.common.di.qualifier.Background
import com.android.xrayfa.common.repository.SettingsRepository
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
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import javax.inject.Inject
import javax.inject.Singleton

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

@Singleton
class XrayCoreManager
@Inject constructor(
    @Application private val context: Context,
    @Background private val coroutineScope: CoroutineScope,
    private val parserFactory: ParserFactory,
    private val settingsRepository: SettingsRepository
): TrafficDetector {

    companion object {
        const val TAG = "XrayCoreManager"
    }
    private var coreController: CoreController? = null
    private var job: Job? = null

    private val _trafficFlow = MutableSharedFlow<Pair<Double, Double>>(replay = 1)
    override val trafficFlow: SharedFlow<Pair<Double, Double>> = _trafficFlow.asSharedFlow()

    val controllerHandler = object: CoreCallbackHandler {
        override fun onEmitStatus(p0: Long, p1: String?): Long {
            Log.i(TAG, "onEmitStatus: $p0 $p1")
            return 0L
        }

        override fun shutdown(): Long {
            Log.i(TAG, "shutdown: end")
            return 0L
        }

        override fun startup(): Long {
            Log.i(TAG, "startup: start")
            return 0L
        }

    }
    init {

        Log.i(TAG, "${context.filesDir.absolutePath}")
        Libv2ray.initCoreEnv(
            context.filesDir.absolutePath, Device.getDeviceIdForXUDPBaseKey()
        )
        coroutineScope.launch {
            val xrayCoreVersion = Libv2ray.checkVersionX()
            if (settingsRepository.settingsFlow.first().xrayCoreVersion != xrayCoreVersion) {
                settingsRepository.setXrayCoreVersion(xrayCoreVersion)
            }
        }
        coreController = Libv2ray.newCoreController(controllerHandler)
    }


    fun measureDelaySync(url: String): Long {
        if (coreController?.isRunning == false) return -1
        return try {
            coreController?.measureDelay(url) ?: 0L
        } catch (e: Exception) {
            Log.e(TAG, "measureDelaySync: ${e.message}")
            -1
        }
    }

    suspend fun startXrayCore(startOptions: StartOptions, tunFd: Int?): Boolean {
        try {
            tunFd?.let {
                coreController?.startLoop(parserFactory.getParser(startOptions.url).parse(startOptions),tunFd)
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

    fun stopXrayCore() {
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
                val up = queryStats(TAG_PROXY, UP_STEAM)
                val down = queryStats(TAG_PROXY, DOWN_STEAM)
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

    /**
     * @param tag direct proxy dns .etc..
     * @param stream uplink or downlink
     * @return traffic todo may be ?
     */
    private fun queryStats(@Tag tag: String, @Stream stream: String): Long {
        return coreController?.queryStats(tag, stream) ?: 0L
    }
}
