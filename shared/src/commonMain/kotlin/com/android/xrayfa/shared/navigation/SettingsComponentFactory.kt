package com.android.xrayfa.shared.navigation

import com.arkivanov.decompose.ComponentContext
import org.koin.mp.KoinPlatform

typealias SettingsComponentFactory = (ComponentContext) -> SettingsComponent

fun defaultSettingsComponentFactory(): SettingsComponentFactory =
    { componentContext ->
        val koin = KoinPlatform.getKoin()
        DefaultSettingsComponent(
            componentContext = componentContext,
            settingsRepository = koin.get(),
            vpnController = koin.get(),
            fileDownloader = koin.get(),
            assetPaths = koin.get(),
        )
    }
