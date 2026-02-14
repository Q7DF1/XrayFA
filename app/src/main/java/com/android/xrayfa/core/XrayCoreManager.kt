package com.android.xrayfa.core

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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import java.util.function.Consumer
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
        const val TAG = "V2rayCoreManager"
    }
    private var coreController: CoreController? = null
    private var job: Job? = null
    private var consumeJob: Job? = null
    private var startOrClose = false
    private val trafficChannel = Channel<Pair<Double, Double>>(capacity = 0)
    private val consumes: MutableList<Consumer<Pair<Double, Double>>> = ArrayList()

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
            if (consumeJob?.isActive == true) consumeJob?.cancel()
            return 0L
        }

        override fun startup(): Long {
            Log.i(TAG, "startup: start")
            consumeJob = coroutineScope.launch(Dispatchers.Default) {
                consumeTraffic()
            }
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
            coreController?.startLoop(parserFactory.getParser(protocol).parse(link),0)
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
                var last = 0L
                var upSpeed: Double
                var downSpeed: Double
                while (true) {
                    var cur = System.currentTimeMillis()
                    val up = queryStats(TAG_PROXY, UP_STEAM)
                    val down = queryStats(TAG_PROXY,DOWN_STEAM)
                    val deltaTimeSec = (cur - last) / 1000.0
                    if (deltaTimeSec > 0) {
                        upSpeed = (up / deltaTimeSec) / 1024
                        downSpeed = (down / deltaTimeSec) / 1024
                    } else {
                        upSpeed = 0.0
                        downSpeed = 0.0
                    }
                    if (last != 0L) {
                        trafficChannel.send(Pair(upSpeed,downSpeed))
                    }else {
                        trafficChannel.send(Pair(0.0,0.0))
                    }
                    last = cur
            }
        }
    }

    override fun stopTrafficDetection() {
        job?.cancel()
        Log.d(TAG, "stopTrafficDetection: ${job?.isActive}")
    }

    override fun addConsumer(consume: Consumer<Pair<Double, Double>>) {
        consumes.add(consume)
    }

    /**
     * transfer the up/download data to ui layer
     */
    override suspend fun consumeTraffic() {

        for (pair in trafficChannel) {
            consumes.forEach {
                it.accept(pair)
            }
            delay(3000L)
        }
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
