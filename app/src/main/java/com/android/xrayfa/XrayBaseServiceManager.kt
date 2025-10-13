package com.android.xrayfa

import android.content.ComponentName
import android.content.Context
import android.content.Context.BIND_AUTO_CREATE
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Message
import android.util.Log
import android.widget.Toast
import com.android.xrayfa.repository.LinkRepository
import com.android.xrayfa.viewmodel.XrayViewmodel.Companion.EXTRA_LINK
import com.android.xrayfa.viewmodel.XrayViewmodel.Companion.EXTRA_PROTOCOL
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class XrayBaseServiceManager
@Inject constructor(
    val repository: LinkRepository
) {

    companion object {
        const val TAG = "XrayBaseServiceManager"
    }

    var messageHandler: (Message)->Unit = {}
    var qsStateCallBack: (Boolean)->Unit = {}
    var viewmodelStateCallback: (Boolean) -> Unit = {}
    val handlerThread = HandlerThread("XrayBaseServiceManager").apply {
        start()
    }
    val H =object: Handler(handlerThread.looper) {
        override fun handleMessage(msg: Message) {
            messageHandler.invoke(msg)
            super.handleMessage(msg)
        }
    }

    suspend fun startXrayBaseService(context: Context): Boolean {

        val first = repository.querySelectedLink().first()
        if (first == null) {
            //
            Toast.makeText(context, R.string.config_not_ready, Toast.LENGTH_SHORT).show()
            return false
        }
        val intent = Intent(context, XrayBaseService::class.java).apply {
            action = "connect"
            putExtra(EXTRA_LINK, first.content)
            putExtra(EXTRA_PROTOCOL, first.protocolPrefix)
        }
        context.startForegroundService(intent)
        Log.i(TAG, "startV2rayService: bind")
        context.bindService(
            Intent(context, XrayBaseService::class.java),
            serviceConnection,
            BIND_AUTO_CREATE
        )
        qsStateCallBack(true)
        viewmodelStateCallback(true)
        return true
    }

    fun stopXrayBaseService(context: Context) {

        val intent = Intent(context, XrayBaseService::class.java).apply {
            action = "disconnect"
        }
        context.unbindService(serviceConnection)
        context.startService(intent)
        qsStateCallBack(false)
        viewmodelStateCallback(false)
    }


    val serviceConnection = object: ServiceConnection {
        override fun onServiceConnected(
            name: ComponentName?,
            service: IBinder?
        ) {
            val binder = (service as XrayBaseService.LocalBinder).getService()
            binder.H = H
        }

        override fun onServiceDisconnected(name: ComponentName?) {
        }

    }
}