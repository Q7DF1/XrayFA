package hev.htproxy

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
import com.android.v2rayForAndroidUI.MainActivity
import com.android.v2rayForAndroidUI.R
import com.android.v2rayForAndroidUI.V2rayCoreManager
import com.android.v2rayForAndroidUI.utils.NetPreferences
import javax.inject.Inject

class V2rayBaseService
@Inject constructor(
    private val tun2SocksService: Tun2SocksService,
    private val v2rayCoreManager: V2rayCoreManager
): VpnService() {

    companion object {
        
        const val TAG = "V2rayCoreService"
        const val CHANNEL_ID = "foreground_service_v2rayFA_channel"
        const val NOTIFICATION_ID = 1
    }

    var tunFd: ParcelFileDescriptor? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        if (intent?.action == "disconnect") {

            Log.i(TAG, "onStartCommand: stop")
            stopV2rayCoreService()
            return  START_NOT_STICKY
        }else {

            Log.i(TAG, "onStartCommand: start")
            startV2rayCoreService()
            return START_STICKY
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.i(TAG, "onCreate: ${getExternalFilesDir("assets")?.absolutePath}")
    }

    override fun onDestroy() {
        Log.i(TAG, "onDestroy: close VPN")
        super.onDestroy()
        tunFd?.close()
        tunFd = null
    }



    private fun startVpn() {
        startForegroundNotification()
        val prefs  = NetPreferences(this)
        val builder = Builder()
        tunFd = builder.setSession(resources.getString(R.string.app_label))
            .addAddress(prefs.tunnelIpv4Address, prefs.tunnelIpv4Prefix)
            .addDisallowedApplication(applicationContext.packageName)
            .addRoute("0.0.0.0",0)
            .setMtu(prefs.tunnelMtu)
            .setBlocking(false)
            .establish()
    }

    private fun stopVPN() {
        tunFd?.close()
        tunFd = null
    }

    private fun startV2rayCoreService() {
        v2rayCoreManager.startV2rayCore()
        startVpn()
        tunFd?.let {
            tun2SocksService.startTun2Socks(it.fd)
        }

        postUpdateForegroundNotification()
    }

    private fun stopV2rayCoreService() {

        tun2SocksService.stopTun2Socks()
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
            this,0,Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
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
        val notification = makeForegroundNotification(false)
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