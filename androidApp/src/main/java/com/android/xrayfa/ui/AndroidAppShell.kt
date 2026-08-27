package com.android.xrayfa.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.android.xrayfa.shared.navigation.RootComponent
import com.android.xrayfa.shared.navigation.createRootComponent
import com.android.xrayfa.shared.ui.RootContent
import com.android.xrayfa.shared.ui.platform.LocalPlatformRootHooks
import com.android.xrayfa.shared.vpn.VpnConnectCoordinator
import com.android.xrayfa.ui.navigation.AndroidRootAction
import com.android.xrayfa.ui.navigation.AndroidRootActionCoordinator
import com.android.xrayfa.viewmodel.AppsViewmodel
import com.android.xrayfa.viewmodel.SettingsViewmodel
import com.android.xrayfa.viewmodel.XrayViewmodel
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import kotlinx.coroutines.flow.collectLatest
import org.koin.compose.koinInject

@Composable
fun AndroidAppShell(
    lifecycle: LifecycleRegistry,
    settingsViewmodel: SettingsViewmodel,
    appsViewmodel: AppsViewmodel,
    xrayViewmodel: XrayViewmodel,
    rootActionCoordinator: AndroidRootActionCoordinator,
    modifier: Modifier = Modifier,
    rootComponent: RootComponent = remember(lifecycle) { createRootComponent(lifecycle) },
) {
    val platformHooks =
        remember(settingsViewmodel, appsViewmodel, xrayViewmodel) {
            AndroidPlatformRootHooks(
                settingsViewmodel = settingsViewmodel,
                appsViewmodel = appsViewmodel,
                xrayViewmodel = xrayViewmodel,
            )
        }
    val vpnConnectCoordinator: VpnConnectCoordinator = koinInject()

    LaunchedEffect(rootActionCoordinator, vpnConnectCoordinator, rootComponent) {
        rootActionCoordinator.pendingAction.collectLatest { action ->
            when (action) {
                AndroidRootAction.OpenQrScan -> {
                    rootComponent.openQrScanner()
                    rootActionCoordinator.consume()
                }
                AndroidRootAction.ConnectVpn -> {
                    rootActionCoordinator.consume()
                    if (vpnConnectCoordinator.prepareConfigForConnect()) {
                        vpnConnectCoordinator.connect()
                    }
                }
                AndroidRootAction.DisconnectVpn -> {
                    rootActionCoordinator.consume()
                    vpnConnectCoordinator.disconnect()
                }
                is AndroidRootAction.OpenScreen -> {
                    rootComponent.openAgentScreen(action.screen)
                    rootActionCoordinator.consume()
                }
                null -> Unit
            }
        }
    }

    CompositionLocalProvider(LocalPlatformRootHooks provides platformHooks) {
        RootContent(
            component = rootComponent,
            modifier = modifier.fillMaxSize(),
        )
    }
}

@Composable
fun rememberAndroidRootLifecycle(): LifecycleRegistry {
    val lifecycle = remember { LifecycleRegistry() }
    DisposableEffect(lifecycle) {
        lifecycle.resume()
        onDispose { lifecycle.destroy() }
    }
    return lifecycle
}
