package com.android.xrayfa.di

import com.android.xrayfa.common.core.TrafficDetector
import com.android.xrayfa.common.core.XrayAssetPaths
import com.android.xrayfa.common.core.XrayCore
import com.android.xrayfa.common.core.VpnStartupStateStore
import com.android.xrayfa.core.AppVpnController
import com.android.xrayfa.core.XrayBaseServiceManager
import com.android.xrayfa.core.XrayCoreManager
import com.android.xrayfa.core.XrayAssetInstaller
import com.android.xrayfa.helper.NotificationHelper
import com.android.xrayfa.nativebridge.NativeBridgeFactory
import com.android.xrayfa.nativebridge.TunBridge
import com.android.xrayfa.nativebridge.XrayBridge
import com.android.xrayfa.repository.AppInfoRepository
import com.android.xrayfa.vpn.VpnController
import com.android.xrayfa.widget.ConnectionWidgetUpdater
import com.android.xrayfa.shared.di.KoinQualifiers
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

val appCoreDiModule: Module = module {
    single {
        ConnectionWidgetUpdater(
            context = androidContext(),
            scope = get(named(KoinQualifiers.BACKGROUND_SCOPE)),
            repository = get(),
            startup = get(),
            core = get(),
            manager = get(),
        )
    }
    single { VpnStartupStateStore() }
    single {
        val context = androidContext()
        XrayAssetInstaller(get<XrayAssetPaths>(), { context.assets.open(it) },
            { android.util.Log.i("XrayAssetInstaller", it) })
    }
    single<XrayBridge> { NativeBridgeFactory.createXrayBridge() }
    single<TunBridge> { NativeBridgeFactory.createTunBridge() }
    single {
        XrayCoreManager(
            context = androidContext(),
            coroutineScope = get(named(KoinQualifiers.BACKGROUND_SCOPE)),
            parserFactory = get(),
            settingsRepository = get(),
            assetPaths = get(),
            xrayBridge = get(),
            assetInstaller = get(),
            startupStateStore = get(),
        )
    }
    single<XrayCore> { get<XrayCoreManager>() }
    single<TrafficDetector> { get<XrayCoreManager>() }
    single { AppInfoRepository(androidContext()) }
    single {
        NotificationHelper(
            settingsRepository = get(),
            backgroundScope = get(named(KoinQualifiers.BACKGROUND_SCOPE)),
            context = androidContext(),
        )
    }
    single {
        XrayBaseServiceManager(
            repository = get(),
            subscriptionRepository = get(),
            trafficDetector = get<TrafficDetector>(),
            context = androidContext(),
            settingsRepository = get(),
            startupStateStore = get(),
        )
    }
    single<VpnController> {
        AppVpnController(
            manager = get(),
            scope = get(named(KoinQualifiers.BACKGROUND_SCOPE)),
            startupStateStore = get(),
            assetInstaller = get(),
        )
    }
}
