package com.android.xrayfa.shared.navigation

import com.arkivanov.decompose.ComponentContext
import org.koin.mp.KoinPlatform

typealias ConfigComponentFactory = (ComponentContext) -> ConfigComponent

fun defaultConfigComponentFactory(
    filterLabels: ConfigFilterLabels = ConfigFilterLabels(),
): ConfigComponentFactory =
    { componentContext ->
        val koin = KoinPlatform.getKoin()
        // Initializes the platform restart preparation hook as well as the home path.
        koin.get<com.android.xrayfa.shared.vpn.VpnConnectCoordinator>()
        DefaultConfigComponent(
            componentContext = componentContext,
            nodeRepository = koin.get(),
            subscriptionRepository = koin.get(),
            vpnController = koin.get(),
            configLinkImporter = koin.get(),
            nodeEditor = koin.get(),
            nodeFormEditor = koin.get(),
            filterLabels = filterLabels,
            settingsRepository = koin.get(),
            xrayCore = koin.get(),
            parserFactory = koin.get(),
            jsonEditor = koin.get(),
        )
    }
