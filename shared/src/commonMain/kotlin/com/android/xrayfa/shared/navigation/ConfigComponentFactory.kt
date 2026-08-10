package com.android.xrayfa.shared.navigation

import com.arkivanov.decompose.ComponentContext
import org.koin.mp.KoinPlatform

typealias ConfigComponentFactory = (ComponentContext) -> ConfigComponent

fun defaultConfigComponentFactory(
    filterLabels: ConfigFilterLabels = ConfigFilterLabels(),
): ConfigComponentFactory =
    { componentContext ->
        val koin = KoinPlatform.getKoin()
        DefaultConfigComponent(
            componentContext = componentContext,
            nodeRepository = koin.get(),
            subscriptionRepository = koin.get(),
            vpnController = koin.get(),
            configLinkImporter = koin.get(),
            nodeEditor = koin.get(),
            nodeFormEditor = koin.get(),
            filterLabels = filterLabels,
        )
    }
