package com.android.xrayfa.helper

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.widget.RemoteViews
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.android.xrayfa.MainActivity
import com.android.xrayfa.R
import com.android.xrayfa.datastore.SettingsRepository
import com.android.xrayfa.datastore.Theme
import com.android.xrayfa.core.XrayBaseService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch


class NotificationHelper(
    val settingsRepository: SettingsRepository,
    val backgroundScope: CoroutineScope,
    val context: Context,
) {

    companion object {
        const val CHANNEL_ID = "foreground_service_v2rayFA_channel_notification_id"
        const val NOTIFICATION_ID = 1
        const val TAG = "NotificationHelper"

        fun canPostPromotionsEnabled(context: Context): Boolean {
            return if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                with(NotificationManagerCompat.from(context)) {
                    canPostPromotedNotifications()
                }
            } else false
        }
    }
    private var notificationView =
        RemoteViews(context.packageName, R.layout.notification_traffic_layout)
    private var bigNotificationView=
        RemoteViews(context.packageName, R.layout.big_notification_traffic_layout)
    val pendingIntent: PendingIntent? = PendingIntent.getActivity(
    context,0, Intent(
            context,
            MainActivity::class.java
        ),
    PendingIntent.FLAG_IMMUTABLE
    )

    val liveBuilder: NotificationCompat.Builder = NotificationCompat.Builder(context,CHANNEL_ID)

        .setContentTitle(context.resources.getString(R.string.app_label))
        .setContentText("${String.format("%.1f",0.0)} kb/s ${String.format("%.1f",0.0)} kb/s")
        .setSmallIcon(R.drawable.ic_small_notification)
        .setContentIntent(pendingIntent)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setCategory(NotificationCompat.CATEGORY_STATUS)
        .setSilent(true)
        .setRequestPromotedOngoing(true)
        .setOngoing(true)
        .setShortCriticalText("XrayFA")
    val normalBuilder: NotificationCompat.Builder = NotificationCompat.Builder(context, CHANNEL_ID)
        .setContentTitle(context.resources.getString(R.string.app_label))
        .setCustomContentView(notificationView)
        .setCustomBigContentView(bigNotificationView)
        .setStyle(NotificationCompat.DecoratedCustomViewStyle())
        .setSmallIcon(R.drawable.ic_small_notification)
        .setContentIntent(pendingIntent)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setOngoing(true)
        .setSilent(true)

    var liveUpdate = false
    private var currentDarkMode: Int = Theme.AUTO_MODE.code

    var preData: Pair<Double,Double>? = null

    init {

        backgroundScope.launch {
            val settings = settingsRepository.settingsFlow.first()
            liveUpdate = settings.liveUpdateNotification
            currentDarkMode = settings.darkMode
            
            settingsRepository.settingsFlow.collect {
                var needsRefresh = false
                if (it.liveUpdateNotification != liveUpdate) {
                    liveUpdate = it.liveUpdateNotification
                    needsRefresh = true
                }
                if (it.darkMode != currentDarkMode) {
                    currentDarkMode = it.darkMode
                    needsRefresh = true
                }
                
                if (needsRefresh) {
                    onSettingsChanged()
                }
            }
        }

        // init button
        val intent = Intent(context, XrayBaseService::class.java).apply {
            action = "disconnect"
        }
        val stopPendingIntent = PendingIntent.getService(
            context,
            1,  // Unique request code
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        normalBuilder.addAction(0, context.getString(R.string.service_close), stopPendingIntent)
    }

    private fun onSettingsChanged() {
        Log.d(TAG, "onSettingsChanged: liveUpdate=$liveUpdate, darkMode=$currentDarkMode")
        val notification = makeNotification(preData ?: Pair(0.0, 0.0))
        updateNotificationChecked(notification)
    }

    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            CHANNEL_ID,
            "Foreground Service Channel",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            setShowBadge(false)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(serviceChannel)
    }
    fun showNotification(service: Service) {
        createNotificationChannel()
        val notification = makeNotification(Pair(0.0,0.0))
        service.startForeground(NOTIFICATION_ID, notification)
    }

    fun hideNotification() {
        with(NotificationManagerCompat.from(context)) {
            cancel(NOTIFICATION_ID)
        }
    }

    /**
     * Re-render and re-post the notification using the latest traffic data.
     * Call this when system configuration (e.g. dark/light mode) changes so the
     * SystemUI can apply the current notification theme.
     */
    fun refreshNotification() {
        val notification = makeNotification(preData ?: Pair(0.0, 0.0))
        updateNotificationChecked(notification)
    }

    fun makeNotification(data: Pair<Double,Double>): Notification {
         return if (liveUpdate && canPostPromotionsEnabled(context)) {
             liveBuilder
                 .setContentText("${String.format("%.1f",data.first)} kb/s ${String.format("%.1f",data.second)} kb/s")
                 .setStyle(NotificationCompat.BigTextStyle()
                     .setBigContentTitle(context.resources.getString(R.string.app_label))
                     .bigText("${String.format("%.1f",data.first)} kb/s ${String.format("%.1f",data.second)} kb/s")
                 )
                 .build()
        } else {
             notificationView.setTextViewText(R.id.stream_up,"${String.format("%.1f",data.first)} kb/s")
             notificationView.setTextViewText(R.id.stream_down,"${String.format("%.1f",data.second)} kb/s")
             bigNotificationView.setTextViewText(R.id.stream_up,"${String.format("%.1f",data.first)} kb/s")
             bigNotificationView.setTextViewText(R.id.stream_down,"${String.format("%.1f",data.second)} kb/s")
            normalBuilder
                .setCustomContentView(notificationView)
                .setCustomBigContentView(bigNotificationView)
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .build()
        }
    }


    /**
     * update the notification, it will shows on *SystemUI* notification panel
     * @param: data upload and download traffic data
     */
    fun updateNotificationIfNeeded(data: Pair<Double,Double>) {
        if (preData?.first != data.first || preData?.second != data.second) {
            preData = data
            val notification = makeNotification(data)
            updateNotificationChecked(notification)
        }
    }


    /**
     * internal system call of update notification which need permission if api over than SDK 33
     * the permission request is at [MainActivity.checkNotificationPermission]
     */
    private fun updateNotificationChecked(notification: Notification) {
        with(NotificationManagerCompat.from(context)) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                notify(NOTIFICATION_ID, notification)
            } else {
                // Handle the case where permission is not granted
                // e.g., log the error or request permission from the user
            }
        }
    }

}