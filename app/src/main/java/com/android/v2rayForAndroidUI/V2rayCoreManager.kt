package com.android.v2rayForAndroidUI

import android.content.Context
import android.util.Log
import com.android.v2rayForAndroidUI.utils.Config
import com.android.v2rayForAndroidUI.utils.Device
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import java.io.File

class V2rayCoreManager(
    private val context: Context,
) {

    companion object {
        const val TAG = "V2rayCoreManager"
    }
    private var coreController: CoreController? = null

    val controllerHandler = object: CoreCallbackHandler {
        override fun onEmitStatus(p0: Long, p1: String?): Long {
            Log.i(TAG, "onEmitStatus: $p1")
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
        Libv2ray.initCoreEnv(
            context.getExternalFilesDir("assets")?.absolutePath, Device.getDeviceIdForXUDPBaseKey()
        )
        coreController = Libv2ray.newCoreController(controllerHandler)
    }


    fun startV2rayCore() {

        Thread {
            try {
                val clientIps = context.assets.open("v2.json")
                val jsonConfig = Config.jsonToString(clientIps)
                coreController?.startLoop(jsonConfig)
            }catch (e: Exception) {
                Log.e(TAG, "startV2rayCore failed: ${e.message}")
            }
        }.start()
    }

    fun stopV2rayCore() {
        coreController?.stopLoop()
    }

}