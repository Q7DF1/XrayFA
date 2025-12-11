package com.android.xrayfa

import android.content.Context
import android.util.Log
import androidx.annotation.StringDef
import com.android.xrayfa.common.di.qualifier.Application
import com.android.xrayfa.common.di.qualifier.Background
import com.android.xrayfa.common.repository.SettingsRepository
import com.android.xrayfa.parser.ParserFactory
import com.android.xrayfa.utils.Device
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.log

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
        const val TAG = "V2rayCoreManager"
    }
    private var coreController: CoreController? = null
    private var job: Job? = null
    private var startOrClose = false
    val controllerHandler = object: CoreCallbackHandler {
        override fun onEmitStatus(p0: Long, p1: String?): Long {
            Log.i(TAG, "onEmitStatus: $p0 $p1")
            if (startOrClose)
                startTrafficDetection()
            else
                stopTrafficDetection()
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
        if (coreController?.isRunning == false) {
            return -1
        }
        var delay = 0L
        try {
            delay = coreController?.measureDelay(url) ?:0L
        }catch (e: Exception) {
            Log.e(TAG, "measureDelaySync: ${e.message}", )
            return -1
        }
        return delay
    }

    suspend fun startV2rayCore(link: String,protocol: String) {
        startOrClose = true
        try {
            coreController?.startLoop(parserFactory.getParser(protocol).parse(link))
        }catch (e: Exception) {
            Log.e(TAG, "startV2rayCore failed: ${e.message}")
        }
    }

    fun stopV2rayCore() {
        startOrClose = false
        coreController?.stopLoop()
    }

    override fun startTrafficDetection() {
            job = coroutineScope.launch(Dispatchers.IO) {
                while (true) {
                    val queryStats = queryStats(TAG_PROXY, UP_STEAM)
                    Log.i(TAG, "startTrafficDetection: $queryStats")
                    delay(1000)
            }
        }
    }


    override fun stopTrafficDetection() {
        job?.cancel()
        Log.d(TAG, "stopTrafficDetection: ${job?.isActive}")
    }

    override suspend fun consumeTraffic(onConsume: suspend (Pair<Long, Long>) -> Unit) {
    }

    /**
     * @param tag direct proxy dns .etc..
     * @param stream uplink or downlink
     * @return traffic todo may be ?
     */
    private fun queryStats(@Tag tag: String, @Stream stream: String): Long {
        coreController?.queryStats(tag, stream)
        return 0L
    }
}