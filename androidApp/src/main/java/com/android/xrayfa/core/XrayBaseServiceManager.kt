package com.android.xrayfa.core

import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.android.xrayfa.R
import com.android.xrayfa.common.core.TrafficDetector
import com.android.xrayfa.core.StartOptions.Companion.EXTRA_START_OPTIONS
import com.android.xrayfa.repository.NodeRepository
import com.android.xrayfa.model.isJsonConfig
import com.android.xrayfa.datastore.SettingsRepository
import com.android.xrayfa.shared.vpn.prepareJsonVpn
import com.android.xrayfa.repository.SubscriptionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class XrayBaseServiceManager(
    val repository: NodeRepository,
    val subscriptionRepository: SubscriptionRepository,
    val trafficDetector: TrafficDetector,
    val context: Context,
    val settingsRepository: SettingsRepository,
) {

    companion object {
        const val TAG = "XrayBaseServiceManager"
    }

    var qsStateCallBack: (Boolean)->Unit = {}


    suspend fun getConfigInformation(): StartOptions? {

        val node = repository.querySelectedNode().first() ?: return null
        if (node.isJsonConfig) {
            prepareJsonVpn(node, settingsRepository.settingsFlow.first())
            return StartOptions(node.url, jsonNodeId = node.id)
        }
        val startOption = StartOptions(node.url)
        val subId = node.subscriptionId
        val subscription = subscriptionRepository.getSubscriptionById(subId).first()
        subscription?.preNodeId?.let {
            val node = repository.loadLinksById(it).first()
            startOption.preUrl = node?.takeUnless { it.isJsonConfig }?.url
        }
        subscription?.nextNodeId?.let {
            val node = repository.loadLinksById(it).first()
            startOption.nextUrl = node?.takeUnless { it.isJsonConfig }?.url
        }
        Log.d(TAG, "getConfigInformation: $startOption")
        return startOption
    }
    suspend fun startXrayBaseService(): Boolean {
        val options = try { getConfigInformation() } catch (e: com.android.xrayfa.config.JsonConfigException) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, R.string.core_start_failed, Toast.LENGTH_SHORT).show()
            }
            return false
        }
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
        context.startForegroundService(intent)
        qsStateCallBack(true)
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
