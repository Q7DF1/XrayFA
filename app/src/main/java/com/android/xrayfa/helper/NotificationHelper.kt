package com.android.xrayfa.helper

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.RemoteViews
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.android.xrayfa.MainActivity
import com.android.xrayfa.R
import com.android.xrayfa.common.di.qualifier.Application
import com.android.xrayfa.common.di.qualifier.Background
import com.android.xrayfa.common.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    val settingsRepository: SettingsRepository,
    @param:Background val backgroundScope:  CoroutineScope,
    @param:Application val context: Context,
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
            }else false
        }
    }
    private var notificationView = RemoteViews("com.android.xrayfa", R.layout.notification_traffic_layout)
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
        .setPriority(NotificationManager.IMPORTANCE_MAX)
        .setCategory(NotificationCompat.CATEGORY_STATUS)
        .setSilent(true)
        .setRequestPromotedOngoing(true)
        .setOngoing(true)
        .setShortCriticalText("XrayFA")
    val normalBuilder: NotificationCompat.Builder = NotificationCompat.Builder(context, CHANNEL_ID)
        .setContentTitle(context.resources.getString(R.string.app_label))
        .setContent(notificationView)
        .setCustomBigContentView(notificationView)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentIntent(pendingIntent)
        .setPriority(NotificationManager.IMPORTANCE_LOW)
        .setSilent(true)

    var liveUpdate = false
    init {

        backgroundScope.launch {
            liveUpdate = settingsRepository.settingsFlow.first().liveUpdateNotification
            settingsRepository.settingsFlow.collect {
                if (it.liveUpdateNotification != liveUpdate)
                    onLiveUpdateChanged(it.liveUpdateNotification)
            }
        }
    }

    private fun onLiveUpdateChanged(live: Boolean) {
        Log.d(TAG, "onLiveUpdateChanged: $liveUpdate")
        liveUpdate = live
        val notification = makeNotification(Pair(0.0, 0.0))
        with(NotificationManagerCompat.from(context)) {
            notify(NOTIFICATION_ID, notification)
        }
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
    fun showNotification() {
        createNotificationChannel()
        val notification = makeNotification(Pair(0.0,0.0))
        with(NotificationManagerCompat.from(context)) {
            notify(NOTIFICATION_ID, notification)
        }
    }

    fun hideNotification() {
        with(NotificationManagerCompat.from(context)) {
            cancel(NOTIFICATION_ID)
        }
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
            normalBuilder.build()
        }
    }


    fun updateNotification(data: Pair<Double,Double>) {
        val notification = makeNotification(data)
        with(NotificationManagerCompat.from(context)) {
            notify(NOTIFICATION_ID, notification)
        }
    }




}