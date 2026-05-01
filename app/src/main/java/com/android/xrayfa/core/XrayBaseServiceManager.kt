package com.android.xrayfa.core

import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.android.xrayfa.R
import com.android.xrayfa.common.di.qualifier.Application
import com.android.xrayfa.core.StartOptions.Companion.EXTRA_START_OPTIONS
import com.android.xrayfa.repository.NodeRepository
import com.android.xrayfa.repository.SubscriptionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class XrayBaseServiceManager
@Inject constructor(
    val repository: NodeRepository,
    val subscriptionRepository: SubscriptionRepository,
    val trafficDetector: TrafficDetector,
    @Application val context: Context
) {

    companion object {
        const val TAG = "XrayBaseServiceManager"
    }

    var qsStateCallBack: (Boolean)->Unit = {}
    var viewmodelTrafficCallback: (Pair<Double, Double>) -> Unit = {}


    suspend fun getConfigInformation(): StartOptions? {

        val node = repository.querySelectedNode().first() ?: return null
        val startOption = StartOptions(node.url)
        val subId = node.subscriptionId
        val subscription = subscriptionRepository.getSubscriptionById(subId).first()
        subscription?.preNodeId?.let {
            val node = repository.loadLinksById(it).first()
            startOption.preUrl = node?.url
        }
        subscription?.nextNodeId?.let {
            val node = repository.loadLinksById(it).first()
            startOption.nextUrl = node?.url
        }
        Log.d(TAG, "getConfigInformation: $startOption")
        return startOption
    }
    suspend fun startXrayBaseService(): Boolean {
        val options = getConfigInformation()
        if (options == null) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, R.string.config_not_ready, Toast.LENGTH_SHORT).show()
            }
            return false
        }

        val intent = Intent(context, XrayBaseService::class.java).apply {
            action = XrayBaseService.CONNECT
            putExtra(EXTRA_START_OPTIONS,options)
        }
        context.startService(intent)
        qsStateCallBack(true)
        trafficDetector.addConsumer({
            viewmodelTrafficCallback(it)
        })
        return true
    }

    fun stopXrayBaseService() {

        val intent = Intent(context, XrayBaseService::class.java).apply {
            action = XrayBaseService.DISCONNECT
        }
        context.startService(intent)
        qsStateCallBack(false)
    }


    suspend fun restartXrayBaseServiceIfNeed() {
        if (XrayBaseService.statusFlow.value) {
            val options = getConfigInformation()
            if (options == null) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, R.string.config_not_ready, Toast.LENGTH_SHORT).show()
                }
                return
            }
            val intent = Intent(context, XrayBaseService::class.java).apply {
                action = XrayBaseService.RESTART
                putExtra(EXTRA_START_OPTIONS,options)
            }
            context.startService(intent)
        }
    }
}