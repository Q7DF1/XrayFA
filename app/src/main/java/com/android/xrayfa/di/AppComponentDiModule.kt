package com.android.xrayfa.di

import com.android.xrayfa.BootBroadcastReceiver
import com.android.xrayfa.ComponentResolver
import com.android.xrayfa.MainActivity
import com.android.xrayfa.core.QuickStartTileService
import com.android.xrayfa.core.XrayBaseService
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import xrayfa.tun2socks.TProxyService
import xrayfa.tun2socks.Tun2SocksService
import xrayfa.tun2socks.utils.Tun2SocksConfigUtil

/**
 * Koin registrations for Android components constructed via [com.android.xrayfa.XrayAppCompatFactory].
 * Uses `factory` for Activity/Service/Receiver (new instance per system instantiation),
 * matching prior Dagger Provider map behaviour.
 */
val appComponentDiModule: Module = module {
    factory { Tun2SocksConfigUtil(settingsRepo = get()) }
    single<Tun2SocksService> {
        TProxyService(
            context = androidContext(),
            util = get(),
        )
    }

    factory {
        MainActivity(
            xrayViewmodelFactory = get(),
            detailViewmodelFactory = get(),
            settingsViewmodelFactory = get(),
            subscriptionViewmodelFactory = get(),
            appViewmodelFactory = get(),
        )
    }
    factory {
        XrayBaseService(
            tun2SocksService = get(),
            xrayCore = get(),
            settingsRepo = get(),
            notificationHelper = get(),
        )
    }
    factory {
        QuickStartTileService(xrayBaseServiceManager = get())
    }
    factory {
        BootBroadcastReceiver(
            manager = get(),
            coroutineScope = get(named(KoinQualifiers.BACKGROUND_SCOPE)),
            settingsRepository = get(),
        )
    }

    single {
        ComponentResolver(
            activityProviders = mapOf(
                MainActivity::class.java to { get<MainActivity>() },
            ),
            serviceProviders = mapOf(
                XrayBaseService::class.java to { get<XrayBaseService>() },
                QuickStartTileService::class.java to { get<QuickStartTileService>() },
            ),
            receiverProviders = mapOf(
                BootBroadcastReceiver::class.java to { get<BootBroadcastReceiver>() },
            ),
        )
    }
}
