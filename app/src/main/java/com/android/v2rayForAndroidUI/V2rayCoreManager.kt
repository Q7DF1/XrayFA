package com.android.v2rayForAndroidUI

import android.content.Context
import android.util.Log
import com.android.v2rayForAndroidUI.di.qualifier.Application
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
import com.android.v2rayForAndroidUI.model.StreamSettingsObject
import com.android.v2rayForAndroidUI.model.UserObject
import com.android.v2rayForAndroidUI.model.VLESSInboundConfigurationObject
import com.android.v2rayForAndroidUI.model.VLESSOutboundConfigurationObject
import com.android.v2rayForAndroidUI.model.XrayConfiguration
import com.android.v2rayForAndroidUI.model.stream.RealitySettings
import com.android.v2rayForAndroidUI.utils.Config
import com.android.v2rayForAndroidUI.utils.Device
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import java.io.File
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
        Libv2ray.initCoreEnv(
            context.getExternalFilesDir("assets")?.absolutePath, Device.getDeviceIdForXUDPBaseKey()
        )
        coreController = Libv2ray.newCoreController(controllerHandler)
    }


    fun startV2rayCore() {

        Thread {
            try {
                val clientIps = context.assets.open("v2.json")
                Log.i(TAG, "startV2rayCore: ${context.assets}")
                val jsonConfig = Config.jsonToString(clientIps)
                coreController?.startLoop(jsonConfig)
            }catch (e: Exception) {
                Log.e(TAG, "startV2rayCore failed: ${e.message}")
            }
        }.start()
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
                    destOverride = listOf("http","lts"),
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
                    )
                )
            )
    }

    fun stopV2rayCore() {
        coreController?.stopLoop()
    }

}