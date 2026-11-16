package com.android.xrayfa.core

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.util.Log
import com.android.xrayfa.common.core.TrafficDetector
import com.android.xrayfa.common.core.StartupStage
import com.android.xrayfa.common.core.VpnStartupStateStore
import com.android.xrayfa.common.core.classifyStartupFailure
import com.android.xrayfa.core.StartOptions.Companion.EXTRA_START_OPTIONS
import com.android.xrayfa.repository.NodeRepository
import com.android.xrayfa.model.isJsonConfig
import com.android.xrayfa.datastore.SettingsRepository
import com.android.xrayfa.shared.vpn.prepareJsonVpn
import com.android.xrayfa.shared.vpn.VpnStartOptionsResolver
import kotlinx.coroutines.flow.first

class XrayBaseServiceManager(
    val repository: NodeRepository,
    private val startOptionsResolver: VpnStartOptionsResolver,
    val trafficDetector: TrafficDetector,
    val context: Context,
    val settingsRepository: SettingsRepository,
    val startupStateStore: VpnStartupStateStore,
) {

    companion object {
        const val TAG = "XrayBaseServiceManager"
    }

    var qsStateCallBack: (Boolean)->Unit = {}


    suspend fun getConfigInformation(): StartOptions? {

        val node = repository.querySelectedNode().first() ?: return null
        val resolved = startOptionsResolver.resolveNode(node)
        if (node.isJsonConfig) {
            prepareJsonVpn(node, settingsRepository.settingsFlow.first(), resolved.jsonConfig)
            return StartOptions(
                url = node.url,
                jsonNodeId = node.id,
                selectedNodeId = node.id,
                nodeName = node.remark,
            )
        }
        val startOption = StartOptions(
            url = resolved.url,
            selectedNodeId = node.id,
            nodeName = node.remark,
            serverAddress = node.address,
            serverPort = node.port,
            preUrl = resolved.preUrl,
            nextUrl = resolved.nextUrl,
            muxSettings = resolved.muxSettings,
            preMuxSettings = resolved.preMuxSettings,
            nextMuxSettings = resolved.nextMuxSettings,
        )
        val usesProxyChain = startOption.preUrl != null || startOption.nextUrl != null
        Log.d(TAG, "getConfigInformation: node selected, chained=$usesProxyChain")
        return startOption
    }
    suspend fun startXrayBaseService(): Boolean {
        val attemptId = startupStateStore.begin()
        var stage = StartupStage.CONFIGURATION
        try {
            val options = getConfigInformation() ?: error("NO_NODE: no selected configuration")
            stage = StartupStage.VPN
            check(VpnService.prepare(context) == null) { "VPN_PERMISSION: VPN permission has not been granted" }
            val intent = Intent(context, XrayBaseService::class.java).apply {
                action = XrayBaseService.CONNECT
                putExtra(EXTRA_START_OPTIONS, options)
                putExtra(XrayBaseService.EXTRA_START_ATTEMPT, attemptId)
            }
            context.startForegroundService(intent)
            qsStateCallBack(true)
            return true
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            startupStateStore.fail(classifyStartupFailure(e, stage), attemptId)
            return false
        }
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
            val attemptId = startupStateStore.begin()
            try {
                val options = getConfigInformation() ?: error("NO_NODE: no selected configuration")
                val intent = Intent(context, XrayBaseService::class.java).apply {
                    action = XrayBaseService.RESTART
                    putExtra(EXTRA_START_OPTIONS, options)
                    putExtra(XrayBaseService.EXTRA_START_ATTEMPT, attemptId)
                }
                context.startService(intent)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                startupStateStore.fail(classifyStartupFailure(e, StartupStage.CONFIGURATION), attemptId)
            }
        }
    }
}
