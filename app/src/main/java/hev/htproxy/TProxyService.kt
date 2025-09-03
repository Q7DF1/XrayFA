package hev.htproxy

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.LocalSocket
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.android.v2rayForAndroidUI.MainActivity
import com.android.v2rayForAndroidUI.R
import com.android.v2rayForAndroidUI.V2rayCoreManager
import com.android.v2rayForAndroidUI.utils.Config
import com.android.v2rayForAndroidUI.utils.Device
import com.android.v2rayForAndroidUI.utils.NetPreferences
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import java.io.File
import java.io.FileOutputStream

class TProxyService: VpnService() {

    private val v2rayCoreManager: V2rayCoreManager? = null
    companion object {
        
        const val TAG = "TProxyService"
        const val CHANNEL_ID = "foreground_service_v2rayFA_channel"
        const val NOTIFICATION_ID = 1
        
        @JvmStatic
        external fun TProxyStartService(configPath: String,fd: Int)
        @JvmStatic
        external fun TProxyStopService()
        @JvmStatic
        external fun TProxyGetStats(): LongArray?


        init {
            System.loadLibrary("hev-socks5-tunnel")
        }
    }

    var coreCallbackHandler = object: CoreCallbackHandler {
        override fun onEmitStatus(p0: Long, p1: String?): Long {
            Log.i(TAG, "onEmitStatus: $p1")
            return 0
        }

        override fun shutdown(): Long {
            return 0
        }

        override fun startup(): Long {
            return 0
        }

    }

    var coreController: CoreController? = null

    var tunFd: ParcelFileDescriptor? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        if (intent?.action == "disconnect") {

            Log.i(TAG, "onStartCommand: stop")
            stopVPN()
            return  START_NOT_STICKY
        }else {

            Log.i(TAG, "onStartCommand: start")
            startVpn()
            return START_STICKY
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.i(TAG, "onCreate: ${getExternalFilesDir("assets")?.absolutePath}")
        Libv2ray.initCoreEnv(getExternalFilesDir("assets")?.absolutePath, Device.getDeviceIdForXUDPBaseKey())
        coreController = Libv2ray.newCoreController(coreCallbackHandler)
    }

    override fun onDestroy() {
        Log.i(TAG, "onDestroy: close VPN")
        super.onDestroy()
        tunFd?.close()
        tunFd = null
    }



    fun startVpn() {
        startForegroundNotification()
        val prefs  = NetPreferences(this)
        val builder = Builder()
        tunFd = builder.setSession(resources.getString(R.string.app_label))
            .addAddress(prefs.tunnelIpv4Address, prefs.tunnelIpv4Prefix)
            .addRoute("0.0.0.0",0)
            .addDnsServer(prefs.dnsIpv4)
            .setMtu(prefs.tunnelMtu)
            .addDisallowedApplication("com.android.v2rayForAndroid")
            .setBlocking(false)
            .establish()

        val ips = assets.open("tun2socks.yaml")
        val configFile = File(cacheDir,"tun2socks1.yaml")
        val fops = FileOutputStream(configFile)
        val bytes = ips.readBytes()
        fops.write(bytes)
        fops.close()
        ips.close()
        Thread {
            try {
                TProxyStartService(configFile.absolutePath,tunFd!!.fd)
            }catch (e : Exception) {
                Log.e(TAG, "startTuntoSocks failed: ${e.message}")
            }
            val notification = makeForegroundNotification(true)

            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, notification)
            val clientIps = assets.open("v2.json")
            val jsonConfig = Config.jsonToString(clientIps)
            Log.i(TAG, "startVpn: $jsonConfig")
            try {
                coreController?.startLoop(jsonConfig)
            }catch (e: Exception) {
                Log.e(TAG, "startVpn: failed to startLoop ${e.message}")
            }
        }.start()
    }

    fun stopVPN() {
        Log.i(TAG, "stopVPN: lishien++")
        stopForeground(STOP_FOREGROUND_REMOVE)
        coreController?.stopLoop()
        stopSelf()
        TProxyStopService()
        tunFd?.close()
        tunFd = null
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