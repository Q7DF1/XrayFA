package com.android.xrayfa.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.android.xrayfa.shared.navigation.DefaultSettingsComponent
import com.android.xrayfa.shared.navigation.SettingsComponent
import com.android.xrayfa.datastore.SettingsRepository
import com.android.xrayfa.vpn.VpnController
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import org.koin.mp.KoinPlatform

@Composable
fun rememberAndroidSettingsComponent(): SettingsComponent {
    val lifecycle = remember { LifecycleRegistry() }
    DisposableEffect(Unit) {
        lifecycle.resume()
        onDispose { lifecycle.destroy() }
    }
    val koin = remember { KoinPlatform.getKoin() }
    return remember(lifecycle) {
        val componentContext: ComponentContext = DefaultComponentContext(lifecycle = lifecycle)
        DefaultSettingsComponent(
            componentContext = componentContext,
            settingsRepository = koin.get<SettingsRepository>(),
            vpnController = koin.get<VpnController>(),
        )
    }
}
