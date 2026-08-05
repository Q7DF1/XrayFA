package com.android.xrayfa.di

import com.android.xrayfa.common.core.XrayCore
import com.android.xrayfa.core.XrayCoreManager
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
    single { AppInfoRepository(androidContext()) }
}
