package com.android.xrayfa.shared.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume

@Composable
fun rememberSubscriptionComponent(
    factory: SubscriptionComponentFactory = defaultSubscriptionComponentFactory(),
): SubscriptionComponent {
    val lifecycle = remember { LifecycleRegistry() }
    DisposableEffect(Unit) {
        lifecycle.resume()
        onDispose { lifecycle.destroy() }
    }
    return remember(lifecycle, factory) {
        factory(DefaultComponentContext(lifecycle = lifecycle))
    }
}
