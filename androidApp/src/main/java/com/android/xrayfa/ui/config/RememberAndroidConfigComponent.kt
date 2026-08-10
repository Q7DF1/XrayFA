package com.android.xrayfa.ui.config

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.android.xrayfa.shared.config.ConfigLinkImporter
import com.android.xrayfa.shared.config.NodeEditor
import com.android.xrayfa.shared.navigation.ConfigComponent
import com.android.xrayfa.shared.navigation.ConfigFilterLabels
import com.android.xrayfa.shared.navigation.DefaultConfigComponent
import com.android.xrayfa.repository.NodeRepository
import com.android.xrayfa.repository.SubscriptionRepository
import com.android.xrayfa.vpn.VpnController
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import org.koin.mp.KoinPlatform

@Composable
fun rememberAndroidConfigComponent(filterLabels: ConfigFilterLabels): ConfigComponent {
    val lifecycle = remember { LifecycleRegistry() }
    DisposableEffect(Unit) {
        lifecycle.resume()
        onDispose { lifecycle.destroy() }
    }
    val koin = remember { KoinPlatform.getKoin() }
    return remember(lifecycle, filterLabels) {
        val componentContext: ComponentContext = DefaultComponentContext(lifecycle = lifecycle)
        DefaultConfigComponent(
            componentContext = componentContext,
            nodeRepository = koin.get<NodeRepository>(),
            subscriptionRepository = koin.get<SubscriptionRepository>(),
            vpnController = koin.get<VpnController>(),
            configLinkImporter = koin.get<ConfigLinkImporter>(),
            nodeEditor = koin.get<NodeEditor>(),
            nodeFormEditor = koin.get(),
            filterLabels = filterLabels,
        )
    }
}
