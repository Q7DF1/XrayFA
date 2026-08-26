package com.android.xrayfa.ui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.android.xrayfa.shared.navigation.RootComponent
import com.android.xrayfa.shared.navigation.RootTab
import com.android.xrayfa.shared.navigation.createRootComponent
import com.android.xrayfa.shared.ui.RootContent
import com.android.xrayfa.shared.ui.platform.LocalPlatformRootHooks
import com.android.xrayfa.shared.ui.platform.PlatformRootHooks
import com.android.xrayfa.shared.vpn.VpnConnectCoordinator
import com.android.xrayfa.ui.component.AndroidAppsScreen
import com.android.xrayfa.ui.component.AndroidLogcatScreen
import com.android.xrayfa.ui.component.AndroidSettingsGeneralViewModelExtras
import com.android.xrayfa.ui.component.AndroidSettingsNetworkViewModelExtras
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
    var openQrScannerRequest by remember { mutableStateOf(false) }
    val vpnConnectCoordinator: VpnConnectCoordinator = koinInject()

    LaunchedEffect(rootActionCoordinator, vpnConnectCoordinator) {
        rootActionCoordinator.pendingAction.collectLatest { action ->
            when (action) {
                AndroidRootAction.OpenQrScan -> {
                    rootComponent.selectTab(RootTab.Config)
                    openQrScannerRequest = true
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
                null -> Unit
            }
        }
    }

    CompositionLocalProvider(LocalPlatformRootHooks provides platformHooks) {
        RootContent(
            component = rootComponent,
            modifier = modifier.fillMaxSize(),
            openQrScannerRequest = openQrScannerRequest,
            onOpenQrScannerRequestConsumed = { openQrScannerRequest = false },
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

private class AndroidPlatformRootHooks(
    private val settingsViewmodel: SettingsViewmodel,
    private val appsViewmodel: AppsViewmodel,
    private val xrayViewmodel: XrayViewmodel,
) : PlatformRootHooks {
    @Composable
    override fun ColumnScope.SettingsGeneralExtras(
        component: com.android.xrayfa.shared.navigation.SettingsComponent,
    ) {
        AndroidSettingsGeneralViewModelExtras(settingsViewmodel)
    }

    @Composable
    override fun ColumnScope.SettingsNetworkExtras(
        component: com.android.xrayfa.shared.navigation.SettingsComponent,
    ) {
        AndroidSettingsNetworkViewModelExtras(settingsViewmodel)
    }

    @Composable
    override fun AppsScreen(
        component: com.android.xrayfa.shared.navigation.SettingsComponent,
        onBack: () -> Unit,
    ) {
        AndroidAppsScreen(viewmodel = appsViewmodel, onBack = onBack)
    }

    @Composable
    override fun LogcatScreen(onBack: () -> Unit) {
        AndroidLogcatScreen(viewmodel = xrayViewmodel, onBack = onBack)
    }
}
