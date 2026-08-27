package com.android.xrayfa.di

import android.content.Intent
import android.net.VpnService
import android.util.Log
import androidx.appfunctions.AppFunctionManager
import com.android.xrayfa.BuildConfig
import com.android.xrayfa.MainActivity
import com.android.xrayfa.agent.AgentActionResult
import com.android.xrayfa.agent.AgentAppInfo
import com.android.xrayfa.agent.AndroidAgentDelayProbe
import com.android.xrayfa.agent.DefaultXrayAgentFacade
import com.android.xrayfa.agent.XrayAgentCatalog
import com.android.xrayfa.agent.XrayAgentFacade
import com.android.xrayfa.agent.XrayAgentNodeQueries
import com.android.xrayfa.agent.appfunctions.AgentAppFunctionEnableSync
import com.android.xrayfa.agent.appfunctions.AgentAppFunctionEnabledWriter
import com.android.xrayfa.datastore.SettingsRepository
import com.android.xrayfa.shared.vpn.TrafficStatsSource
import com.android.xrayfa.ui.navigation.AndroidRootAction
import com.android.xrayfa.ui.navigation.AndroidRootActionCoordinator
import com.android.xrayfa.vpn.AndroidTrafficStatsSource
import kotlinx.coroutines.flow.first
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

private const val AGENT_SYNC_TAG = "AgentAppFunctionSync"

val appAgentDiModule: Module = module {
    single<XrayAgentNodeQueries> { XrayAgentCatalog(get(), get()) }
    single<TrafficStatsSource> { AndroidTrafficStatsSource(get()) }
    single { AndroidAgentDelayProbe(get(), get(), get()) }
    single {
        val manager = AppFunctionManager.getInstance(androidContext())
        AgentAppFunctionEnableSync(
            writer = manager?.let { mgr ->
                AgentAppFunctionEnabledWriter { id, enabled ->
                    mgr.setAppFunctionEnabled(
                        id,
                        if (enabled) {
                            AppFunctionManager.APP_FUNCTION_STATE_ENABLED
                        } else {
                            AppFunctionManager.APP_FUNCTION_STATE_DISABLED
                        },
                    )
                }
            },
            onError = { id, error ->
                Log.w(AGENT_SYNC_TAG, "setAppFunctionEnabled failed for $id", error)
            },
        )
    }
    single<XrayAgentFacade> {
        val context = androidContext()
        val coordinator = get<AndroidRootActionCoordinator>()
        val delayProbe = get<AndroidAgentDelayProbe>()
        DefaultXrayAgentFacade(
            catalog = get(),
            vpnController = get(),
            loadSettings = { get<SettingsRepository>().settingsFlow.first() },
            trafficStatsSource = get(),
            appInfo = AgentAppInfo(
                versionName = BuildConfig.VERSION_NAME,
                versionCode = BuildConfig.VERSION_CODE,
            ),
            vpnConnectCoordinator = get(),
            vpnPermissionGranted = { VpnService.prepare(context) == null },
            requestVpnConsent = {
                val prepare = VpnService.prepare(context)
                if (prepare != null) {
                    prepare.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(prepare)
                }
            },
            measureDelay = { nodeId, url -> delayProbe.measure(nodeId, url) },
            openScreenAction = { screen ->
                context.startActivity(
                    Intent(context, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    },
                )
                coordinator.dispatch(AndroidRootAction.OpenScreen(screen))
                AgentActionResult.Success()
            },
        )
    }
}
