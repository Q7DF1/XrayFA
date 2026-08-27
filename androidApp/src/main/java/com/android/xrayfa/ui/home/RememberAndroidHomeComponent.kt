package com.android.xrayfa.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.android.xrayfa.repository.NodeRepository
import com.android.xrayfa.shared.navigation.DefaultHomeComponent
import com.android.xrayfa.shared.navigation.HomeComponent
import com.android.xrayfa.shared.vpn.TrafficStatsSource
import com.android.xrayfa.shared.vpn.VpnConnectCoordinator
import com.android.xrayfa.vpn.VpnController
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import org.koin.mp.KoinPlatform

@Composable
fun rememberAndroidHomeComponent(): HomeComponent {
    val lifecycle = remember { LifecycleRegistry() }
    DisposableEffect(Unit) {
        lifecycle.resume()
        onDispose { lifecycle.destroy() }
    }
    val koin = remember { KoinPlatform.getKoin() }
    return remember(lifecycle) {
        val componentContext: ComponentContext = DefaultComponentContext(lifecycle = lifecycle)
        DefaultHomeComponent(
            componentContext = componentContext,
            vpnController = koin.get<VpnController>(),
            nodeRepository = koin.get<NodeRepository>(),
            coordinator = koin.get<VpnConnectCoordinator>(),
            trafficStatsSource = koin.get<TrafficStatsSource>(),
            settingsRepository = koin.get(),
            xrayCore = koin.get(),
            parserFactory = koin.get(),
        )
    }
}
