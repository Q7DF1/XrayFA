package com.android.xrayfa

import android.content.Context
import android.util.Log
import com.android.xrayfa.common.di.qualifier.Application
import com.android.xrayfa.parser.ParserFactory
import com.android.xrayfa.utils.Device
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class XrayCoreManager
@Inject constructor(
    @Application private val context: Context,
    @Application private val coroutineScope: CoroutineScope,
    private val parserFactory: ParserFactory
) {

    companion object {
        const val TAG = "V2rayCoreManager"
    }
    private var coreController: CoreController? = null
    private var startOrClose = false
    var trafficDetector: TrafficDetector? = null
    val controllerHandler = object: CoreCallbackHandler {
        override fun onEmitStatus(p0: Long, p1: String?): Long {
            Log.i(TAG, "onEmitStatus: $p0 $p1")
            if (startOrClose)
                trafficDetector?.startTrafficDetection()
            else
                trafficDetector?.stopTrafficDetection()
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

}