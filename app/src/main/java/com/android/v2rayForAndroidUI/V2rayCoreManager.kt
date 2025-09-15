package com.android.v2rayForAndroidUI

import android.content.Context
import android.util.Log
import hev.htproxy.di.qualifier.Application
import com.android.v2rayForAndroidUI.model.DnsObject
import com.android.v2rayForAndroidUI.model.InboundObject
import com.android.v2rayForAndroidUI.model.LogObject
import com.android.v2rayForAndroidUI.model.MuxObject
import com.android.v2rayForAndroidUI.model.OutboundObject
import com.android.v2rayForAndroidUI.model.RoutingObject
import com.android.v2rayForAndroidUI.model.RuleObject
import com.android.v2rayForAndroidUI.model.ServerObject
import com.android.v2rayForAndroidUI.model.SniffingObject
import com.android.v2rayForAndroidUI.model.SocksInboundConfigurationObject
import com.android.v2rayForAndroidUI.model.stream.StreamSettingsObject
import com.android.v2rayForAndroidUI.model.UserObject
import com.android.v2rayForAndroidUI.model.VLESSOutboundConfigurationObject
import com.android.v2rayForAndroidUI.model.XrayConfiguration
import com.android.v2rayForAndroidUI.model.stream.RealitySettings
import com.android.v2rayForAndroidUI.parser.VLESSConfigParser
import com.android.v2rayForAndroidUI.parser.VMESSConfigParser
import com.android.v2rayForAndroidUI.utils.Device
import com.google.gson.Gson
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import javax.inject.Inject

class V2rayCoreManager
@Inject constructor(
    @Application private val context: Context
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
        Log.i(TAG, "${context.getExternalFilesDir("assets")?.absolutePath}: lishien++")
        Libv2ray.initCoreEnv(
            context.getExternalFilesDir("assets")?.absolutePath, Device.getDeviceIdForXUDPBaseKey()
        )
        coreController = Libv2ray.newCoreController(controllerHandler)
    }


    fun startV2rayCore(config: String? = null) {

        Thread {
            try {
                val v2rayConfig = getV2rayConfig()

                coreController?.startLoop(config?: v2rayConfig)
            }catch (e: Exception) {
                Log.e(TAG, "startV2rayCore failed: ${e.message}")
            }
        }.start()
    }
    
    fun getV2rayConfig(): String {

        //val config = VLESSConfigParser().parse("vless://dc503d2f-9028-480f-9ebb-5bd46cfc969b@face.woxiangbaofu.click:443?encryption=none&security=tls&type=ws&host=face.woxiangbaofu.click&path=%2Fdc503d2f-9028-480f-9ebb-5bd46cfc969b#233boy-ws-face.woxiangbaofu.click")
        //val config = VLESSConfigParser().parse("vless://bc313a85-45dd-4904-80dc-37496b18e222@67.230.172.249:18880?encryption=none&flow=xtls-rprx-vision&security=reality&sni=www.paypal.com&fp=chrome&pbk=1CgDPWbxKfcyOa91dLnRxDZ3EuaEbU0GwFnkTIg2XWc&type=tcp&headerType=none#233boy-tcp-67.230.172.249")
        val parser = VMESSConfigParser()
        val config = parser.parse("vmess://eyJ2IjoyLCJwcyI6IjIzM2JveS13cy1mYWNlLndveGlhbmdiYW9mdS5jbGljayIsImFkZCI6ImZhY2Uud294aWFuZ2Jhb2Z1LmNsaWNrIiwicG9ydCI6IjQ0MyIsImlkIjoiZWQ5MzQzYzUtZTg3MC00ZTFiLWE1MTYtNGQzYzAxMjhkYmMwIiwiYWlkIjoiMCIsIm5ldCI6IndzIiwiaG9zdCI6ImZhY2Uud294aWFuZ2Jhb2Z1LmNsaWNrIiwicGF0aCI6Ii9lZDkzNDNjNS1lODcwLTRlMWItYTUxNi00ZDNjMDEyOGRiYzAiLCJ0bHMiOiJ0bHMifQ==")

        return config
    }

    fun getXrayConfiguration(): XrayConfiguration {
        val dns = DnsObject(
            hosts = mapOf(
                "domain:googleapis.cn" to "googleapis.com"
            ),
            servers = listOf(
                "8.8.8.8"
            )
        )
        val inbounds = listOf(
            InboundObject(
                listen = "127.0.0.1",
                port = 10808,
                protocol = "socks",
                settings = SocksInboundConfigurationObject(
                    auth = "noauth",
                    udp = true,
                    userLevel = 8
                ),
                sniffing = SniffingObject(
                    destOverride = listOf("http","tls"),
                    enabled = true
                ),
                tag = "socks"
            )
        )
        val xrayConfig = XrayConfiguration(
            dns = dns,
            inbounds = inbounds,
            log = LogObject(logLevel = "warning"),
            outbounds = listOf(
                OutboundObject(
                    mux = MuxObject(
                        concurrency =  -1,
                        enable = false,
                        xudpConcurrency = 8,
                        xudpProxyUDP443 = ""
                    ),
                    protocol = "vless",
                    settings = VLESSOutboundConfigurationObject(
                        vnext = listOf(
                            ServerObject(
                                address = "67.230.172.249",
                                port = 18880,
                                users = listOf(
                                    UserObject(
                                        encryption = "none",
                                        flow = "xtls-rprx-vision",
                                        id = "bc313a85-45dd-4904-80dc-37496b18e222",
                                        level = 8
                                    )
                            )
                        )
                    )
                ),
                    streamSettings = StreamSettingsObject(
                        network = "tcp",
                        realitySettings = RealitySettings(
                            fingerprint = "chrome",
                            serverName = "www.paypal.com",
                            shortId = "",
                            spiderX = "",
                            publicKey = "1CgDPWbxKfcyOa91dLnRxDZ3EuaEbU0GwFnkTIg2XWc",
                            show = false
                        ),
                        security = "reality"
                    ),
                    tag = "proxy"
            ),
                OutboundObject(
                    protocol = "freedom",
                    settings = null,
                    tag = "direct"
                ),
        ),
            routing = RoutingObject(
                domainStrategy = "IPIfNonMatch",
                rules = listOf(
                    RuleObject(
                        //不正确
                        outboundTag = "proxy",
                        domain = listOf("geolocation-!cn")
                    )
                )
            )
        )
        return xrayConfig
    }

    fun stopV2rayCore() {
        coreController?.stopLoop()
    }

}