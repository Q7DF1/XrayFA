package com.android.xrayfa.shared.navigation

import com.arkivanov.decompose.ComponentContext
import org.koin.mp.KoinPlatform

typealias HomeComponentFactory = (ComponentContext) -> HomeComponent

fun defaultHomeComponentFactory(): HomeComponentFactory =
    { componentContext ->
        val koin = KoinPlatform.getKoin()
        DefaultHomeComponent(
            componentContext = componentContext,
            vpnController = koin.get(),
            nodeRepository = koin.get(),
            coordinator = koin.get(),
            trafficStatsSource = koin.get(),
            settingsRepository = koin.get(),
            xrayCore = koin.get(),
            parserFactory = koin.get(),
        )
    }
