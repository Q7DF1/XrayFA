package com.android.v2rayForAndroidUI

import android.content.Context
import android.util.Log
import hev.htproxy.di.qualifier.Application
import com.android.v2rayForAndroidUI.model.DnsObject
import com.android.v2rayForAndroidUI.model.InboundObject
import com.android.v2rayForAndroidUI.model.LogObject
import com.android.v2rayForAndroidUI.model.MuxObject
import com.android.v2rayForAndroidUI.model.OutboundObject
import com.android.v2rayForAndroidUI.model.PolicyObject
import com.android.v2rayForAndroidUI.model.RoutingObject
import com.android.v2rayForAndroidUI.model.RuleObject
import com.android.v2rayForAndroidUI.model.ServerObject
import com.android.v2rayForAndroidUI.model.SniffingObject
import com.android.v2rayForAndroidUI.model.SocksInboundConfigurationObject
import com.android.v2rayForAndroidUI.model.SystemPolicyObject
import com.android.v2rayForAndroidUI.model.stream.StreamSettingsObject
import com.android.v2rayForAndroidUI.model.UserObject
import com.android.v2rayForAndroidUI.model.VLESSOutboundConfigurationObject
import com.android.v2rayForAndroidUI.model.XrayConfiguration
import com.android.v2rayForAndroidUI.model.stream.RealitySettings
import com.android.v2rayForAndroidUI.parser.VLESSConfigParser
import com.android.v2rayForAndroidUI.parser.VMESSConfigParser
import com.android.v2rayForAndroidUI.rpc.XrayStatsClient
import com.android.v2rayForAndroidUI.utils.Device
import com.google.gson.Gson
import hev.htproxy.di.qualifier.Background
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import java.util.concurrent.Executor
import javax.inject.Inject

class V2rayCoreManager
@Inject constructor(
    @Application private val context: Context,
    @Background private val bgExecutor: Executor
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
        Log.i(TAG, "${context.getExternalFilesDir("assets")?.absolutePath}: lishien++")
        Libv2ray.initCoreEnv(
            context.getExternalFilesDir("assets")?.absolutePath, Device.getDeviceIdForXUDPBaseKey()
        )
        coreController = Libv2ray.newCoreController(controllerHandler)
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun startCountTest() {
        val client = XrayStatsClient()
        client.connect()

// 每秒查询一次
        GlobalScope.launch {
            var lastUp = 0L
            var lastDown = 0L
            while (true) {
                val (uplink, downlink) = client.getTraffic("proxy")
                val upSpeed = uplink - lastUp
                val downSpeed = downlink - lastDown
                lastUp = uplink
                lastDown = downlink

                println("↑ $upSpeed B/s   ↓ $downSpeed B/s")
                delay(1000)
            }
        }

    }

    fun startV2rayCore(config: String? = null) {
        startOrClose = true
        bgExecutor.execute {
            try {
                val v2rayConfig = getV2rayConfig()

                coreController?.startLoop(config?: v2rayConfig)
            }catch (e: Exception) {
                Log.e(TAG, "startV2rayCore failed: ${e.message}")
            }
        }
    }
    
    fun getV2rayConfig(): String {

//        val config = VLESSConfigParser().parse("vless://dc503d2f-9028-480f-9ebb-5bd46cfc969b@face.woxiangbaofu.click:443?encryption=none&security=tls&type=ws&host=face.woxiangbaofu.click&path=%2Fdc503d2f-9028-480f-9ebb-5bd46cfc969b#233boy-ws-face.woxiangbaofu.click")
        val config = VLESSConfigParser().parse("vless://bc313a85-45dd-4904-80dc-37496b18e222@67.230.172.249:18880?encryption=none&flow=xtls-rprx-vision&security=reality&sni=www.paypal.com&fp=chrome&pbk=1CgDPWbxKfcyOa91dLnRxDZ3EuaEbU0GwFnkTIg2XWc&type=tcp&headerType=none#233boy-tcp-67.230.172.249")
        val parser = VMESSConfigParser()
//        val config = parser.parse("vmess://eyJ2IjoyLCJwcyI6IjIzM2JveS13cy1mYWNlLndveGlhbmdiYW9mdS5jbGljayIsImFkZCI6ImZhY2Uud294aWFuZ2Jhb2Z1LmNsaWNrIiwicG9ydCI6IjQ0MyIsImlkIjoiZWQ5MzQzYzUtZTg3MC00ZTFiLWE1MTYtNGQzYzAxMjhkYmMwIiwiYWlkIjoiMCIsIm5ldCI6IndzIiwiaG9zdCI6ImZhY2Uud294aWFuZ2Jhb2Z1LmNsaWNrIiwicGF0aCI6Ii9lZDkzNDNjNS1lODcwLTRlMWItYTUxNi00ZDNjMDEyOGRiYzAiLCJ0bHMiOiJ0bHMifQ==")

        return config
    }

    fun stopV2rayCore() {
        startOrClose = false
        coreController?.stopLoop()
    }

}