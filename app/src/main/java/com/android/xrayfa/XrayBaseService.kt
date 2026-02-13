package com.android.xrayfa

import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import com.android.xrayfa.common.repository.SettingsRepository
import com.android.xrayfa.helper.NotificationHelper
import com.android.xrayfa.utils.EventBus
import com.android.xrayfa.viewmodel.XrayViewmodel.Companion.EXTRA_LINK
import com.android.xrayfa.viewmodel.XrayViewmodel.Companion.EXTRA_PROTOCOL
import xrayfa.tun2socks.utils.NetPreferences
import xrayfa.tun2socks.Tun2SocksService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

class XrayBaseService
@Inject constructor(
    private val tun2SocksService: Tun2SocksService,
    private val xrayCoreManager: XrayCoreManager,
    private val settingsRepo: SettingsRepository,
    private val notificationHelper: NotificationHelper
): VpnService(){

    companion object {

        const val TAG = "XrayBaseService"
        var isRunning: Boolean = false
    }

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)

    var tunFd: ParcelFileDescriptor? = null


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        if (intent?.action == "disconnect") {

            Log.i(TAG, "onStartCommand: stop...")
            stopV2rayCoreService()
            isRunning = false
            EventBus.statusFlow.tryEmit(isRunning)
            return  START_NOT_STICKY
        }else {
            Log.i(TAG, "onStartCommand: start...")
            val link = intent?.getStringExtra(EXTRA_LINK)
            val protocol = intent?.getStringExtra(EXTRA_PROTOCOL)
            startV2rayCoreService(link!!,protocol!!)
            isRunning = true
            EventBus.statusFlow.tryEmit(isRunning)
            return START_STICKY
        }
    }


    override fun onDestroy() {
        Log.i(TAG, "onDestroy: close VPN")
        super.onDestroy()
        tunFd?.close()
        tunFd = null
    }



    private suspend fun startVpn() {
        val prefs  = NetPreferences(this)
        val builder = Builder()
        val allowedPackages = settingsRepo.getAllowedPackages()
        if (!allowedPackages.isEmpty()) {
            allowedPackages.forEach {
                builder.addAllowedApplication(it)
            }
        }else {
            builder.addDisallowedApplication(applicationContext.packageName)
        }
        if (settingsRepo.settingsFlow.first().ipV6Enable) {
            builder.addAddress(prefs.tunnelIpv6Address,prefs.tunnelIpv6Prefix)
        }
        tunFd = builder.setSession(resources.getString(R.string.app_label))
            .addAddress(prefs.tunnelIpv4Address, prefs.tunnelIpv4Prefix)
            .addRoute("0.0.0.0",0)
            .setMtu(prefs.tunnelMtu)
            .setBlocking(false)
            .establish()
    }

    private fun stopVPN() {
        tunFd?.close()
        tunFd = null
    }



    private fun startV2rayCoreService(link: String,protocol: String) {
//        val notification = notificationHelper.makeNotification(Pair(0.0,0.0))
//        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
//            startForeground(
//                NotificationHelper.NOTIFICATION_ID,
//                notification,
//                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
//            )
//        }else startForeground(NotificationHelper.NOTIFICATION_ID,notification)
        serviceScope.launch {
            notificationHelper.showNotification()
//            xrayCoreManager.addConsumer { data->
//                notificationHelper.updateNotification(data)
//            }
            xrayCoreManager.startV2rayCore(link,protocol)
            startVpn()
            tunFd?.let {
                tun2SocksService.startTun2Socks(it.fd)
            }
        }

        
    }

    private fun stopV2rayCoreService() {
        serviceScope.launch {
            tun2SocksService.stopTun2Socks()
        }
        stopVPN()
        xrayCoreManager.stopV2rayCore()
        stopSelf()
        notificationHelper.hideNotification()
    }

}