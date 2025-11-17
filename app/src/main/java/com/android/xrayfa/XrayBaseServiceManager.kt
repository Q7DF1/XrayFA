package com.android.xrayfa

import android.content.Context
import android.content.Intent
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
    val repository: LinkRepository,
    val trafficDetector: TrafficDetector
) {

    companion object {
        const val TAG = "XrayBaseServiceManager"
    }

    var qsStateCallBack: (Boolean)->Unit = {}
    var viewmodelTrafficCallback: (Pair<Long,Long>) -> Unit = {}

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

        qsStateCallBack(true)
        trafficDetector.consumeTraffic{ pair ->
            viewmodelTrafficCallback(pair)
        }
        return true
    }

    fun stopXrayBaseService(context: Context) {

        val intent = Intent(context, XrayBaseService::class.java).apply {
            action = "disconnect"
        }
        context.startService(intent)
        qsStateCallBack(false)
    }
}