package com.android.xrayfa.shared.navigation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume

fun createRootComponent(lifecycle: LifecycleRegistry): RootComponent =
    DefaultRootComponent(
        componentContext = DefaultComponentContext(lifecycle = lifecycle),
    )

/** Convenience for previews/tests; caller must [LifecycleRegistry.resume] before use. */
fun createRootComponent(): RootComponent {
    val lifecycle = LifecycleRegistry()
    lifecycle.resume()
    return createRootComponent(lifecycle)
}
