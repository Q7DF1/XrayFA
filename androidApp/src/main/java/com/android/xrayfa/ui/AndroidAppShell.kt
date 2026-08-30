package com.android.xrayfa.ui

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.android.xrayfa.shared.navigation.RootComponent
import com.android.xrayfa.shared.navigation.RootStackConfig
import com.android.xrayfa.shared.navigation.createRootComponent
import com.android.xrayfa.shared.ui.RootContent
import com.android.xrayfa.shared.ui.platform.LocalPlatformRootHooks
import com.android.xrayfa.shared.vpn.VpnConnectCoordinator
import com.android.xrayfa.ui.navigation.AndroidRootAction
import com.android.xrayfa.ui.navigation.AndroidRootActionCoordinator
import com.android.xrayfa.viewmodel.AppsViewmodel
import com.android.xrayfa.viewmodel.SettingsViewmodel
import com.android.xrayfa.viewmodel.XrayViewmodel
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.arkivanov.essenty.backhandler.BackDispatcher
import com.arkivanov.essenty.backhandler.BackEvent
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import kotlinx.coroutines.CancellationException
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
    rootComponent: RootComponent? = null,
) {
    val backDispatcher = remember { BackDispatcher() }
    val resolvedRoot =
        rootComponent
            ?: remember(lifecycle) {
                createRootComponent(
                    DefaultComponentContext(
                        lifecycle = lifecycle,
                        backHandler = backDispatcher,
                    ),
                )
            }
    val platformHooks =
        remember(settingsViewmodel, appsViewmodel, xrayViewmodel) {
            AndroidPlatformRootHooks(
                settingsViewmodel = settingsViewmodel,
                appsViewmodel = appsViewmodel,
                xrayViewmodel = xrayViewmodel,
            )
        }
    val vpnConnectCoordinator: VpnConnectCoordinator = koinInject()
    val stack by resolvedRoot.stack.subscribeAsState()
    val stackIdle = stack.active.configuration is RootStackConfig.Idle

    // The system gesture (enableOnBackInvokedCallback=true) drives the Decompose
    // BackDispatcher, so RootContent animates the pop instead of snapping back.
    // Enabled only while a stack page is open; otherwise the gesture must exit the app.
    PredictiveBackHandler(enabled = !stackIdle) { progress ->
        var started = false
        try {
            progress.collect { event ->
                val backEvent = event.toEssentyBackEvent()
                if (started) {
                    backDispatcher.progressPredictiveBack(backEvent)
                } else {
                    started = backDispatcher.startPredictiveBack(backEvent)
                }
            }
            backDispatcher.back()
        } catch (e: CancellationException) {
            if (started) backDispatcher.cancelPredictiveBack()
        }
    }

    LaunchedEffect(rootActionCoordinator, vpnConnectCoordinator, resolvedRoot) {
        rootActionCoordinator.pendingAction.collectLatest { action ->
            when (action) {
                AndroidRootAction.OpenQrScan -> {
                    resolvedRoot.openQrScanner()
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
                    resolvedRoot.openAgentScreen(action.screen)
                    rootActionCoordinator.consume()
                }
                null -> Unit
            }
        }
    }

    CompositionLocalProvider(LocalPlatformRootHooks provides platformHooks) {
        RootContent(
            component = resolvedRoot,
            modifier = modifier.fillMaxSize(),
        )
    }
}

private fun BackEventCompat.toEssentyBackEvent(): BackEvent =
    BackEvent(
        progress = progress,
        swipeEdge =
            when (swipeEdge) {
                BackEventCompat.EDGE_LEFT -> BackEvent.SwipeEdge.LEFT
                BackEventCompat.EDGE_RIGHT -> BackEvent.SwipeEdge.RIGHT
                else -> BackEvent.SwipeEdge.UNKNOWN
            },
        touchX = touchX,
        touchY = touchY,
    )

@Composable
fun rememberAndroidRootLifecycle(): LifecycleRegistry {
    val lifecycle = remember { LifecycleRegistry() }
    DisposableEffect(lifecycle) {
        lifecycle.resume()
        onDispose { lifecycle.destroy() }
    }
    return lifecycle
}
