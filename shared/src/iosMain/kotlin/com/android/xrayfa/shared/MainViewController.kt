package com.android.xrayfa.shared

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.window.ComposeUIViewController
import com.android.xrayfa.shared.navigation.createRootComponent
import com.android.xrayfa.shared.ui.AppShell
import com.android.xrayfa.shared.ui.platform.IosPlatformRootHooks
import com.android.xrayfa.shared.ui.platform.LocalPlatformRootHooks
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import platform.UIKit.UIViewController

/** iOS entry: embed in SwiftUI via [UIViewControllerRepresentable]. */
fun MainViewController(): UIViewController {
    IosKoinInit.ensureStarted()
    val lifecycle = LifecycleRegistry()
    val rootComponent = createRootComponent(lifecycle)
    return ComposeUIViewController {
        DisposableEffect(Unit) {
            lifecycle.resume()
            onDispose { lifecycle.destroy() }
        }
        CompositionLocalProvider(LocalPlatformRootHooks provides IosPlatformRootHooks) {
            AppShell(rootComponent = rootComponent)
        }
    }
}
