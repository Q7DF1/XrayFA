package com.android.xrayfa

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.android.xrayfa.common.repository.SettingsRepository
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
    private val v2rayCoreManager: XrayCoreManager,
    private val trafficDetector: TrafficDetector,
    private val settingsRepo: SettingsRepository
): VpnService(){

    companion object {

        const val TAG = "XrayBaseService"
        const val CHANNEL_ID = "foreground_service_v2rayFA_channel"
        const val NOTIFICATION_ID = 1
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

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onDestroy() {
        Log.i(TAG, "onDestroy: close VPN")
        super.onDestroy()
        tunFd?.close()
        tunFd = null
    }



    private suspend fun startVpn() {
        startForegroundNotification()
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
        v2rayCoreManager.trafficDetector = trafficDetector
        serviceScope.launch {
            v2rayCoreManager.startV2rayCore(link,protocol)
            startVpn()
            tunFd?.let {
                tun2SocksService.startTun2Socks(it.fd)
            }
        }

        
        //postUpdateForegroundNotification()
    }

    private fun stopV2rayCoreService() {
        serviceScope.launch {
            tun2SocksService.stopTun2Socks()
        }
        stopVPN()
        v2rayCoreManager.stopV2rayCore()
        stopSelf()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun makeForegroundNotification(connected: Boolean): Notification {
        var content :String = if (connected)
            resources.getString(R.string.connected)
        else resources.getString(R.string.connecting)

        val pendingIntent = PendingIntent.getActivity(
            this,0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(resources.getString(R.string.app_label))
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .build()

        return notification
    }
    private fun startForegroundNotification() {
        val notification = makeForegroundNotification(true) // todo 直接
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun postUpdateForegroundNotification() {
        val notification = makeForegroundNotification(true)
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Foreground Service Channel",
                NotificationManager.IMPORTANCE_HIGH
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }
}