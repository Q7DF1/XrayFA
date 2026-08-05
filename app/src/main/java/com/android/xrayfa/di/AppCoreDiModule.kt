package com.android.xrayfa.di

import com.android.xrayfa.common.core.TrafficDetector
import com.android.xrayfa.common.core.XrayAssetPaths
import com.android.xrayfa.common.core.XrayCore
import com.android.xrayfa.core.XrayBaseServiceManager
import com.android.xrayfa.core.XrayCoreManager
import com.android.xrayfa.helper.NotificationHelper
import com.android.xrayfa.repository.AppInfoRepository
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

val appCoreDiModule: Module = module {
    single {
        XrayCoreManager(
            context = androidContext(),
            coroutineScope = get(named(KoinQualifiers.BACKGROUND_SCOPE)),
            parserFactory = get(),
            settingsRepository = get(),
            assetPaths = get(),
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
        )
    }
}
