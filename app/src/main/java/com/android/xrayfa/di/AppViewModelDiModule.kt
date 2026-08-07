package com.android.xrayfa.di

import com.android.xrayfa.common.core.XrayAssetPaths
import com.android.xrayfa.common.core.XrayCore
import com.android.xrayfa.datastore.SettingsRepository
import com.android.xrayfa.parser.ParserFactory
import com.android.xrayfa.parser.SubscriptionParser
import com.android.xrayfa.repository.AppInfoRepository
import com.android.xrayfa.repository.NodeRepository
import com.android.xrayfa.repository.SubscriptionRepository
import com.android.xrayfa.vpn.VpnController
import com.android.xrayfa.viewmodel.AppsViewmodelFactory
import com.android.xrayfa.viewmodel.DetailViewmodelFactory
import com.android.xrayfa.viewmodel.SettingsViewmodelFactory
import com.android.xrayfa.viewmodel.SubscriptionViewmodelFactory
import com.android.xrayfa.viewmodel.XrayViewmodelFactory
import org.koin.core.module.Module
import org.koin.dsl.module

val appViewModelDiModule: Module = module {
    single {
        XrayViewmodelFactory(
            repository = get(),
            subscriptionRepository = get(),
            vpnController = get(),
            xrayCore = get(),
            settingsRepository = get(),
            parserFactory = get(),
            subscriptionParser = get(),
        )
    }
    single {
        SettingsViewmodelFactory(
            repository = get(),
            fileDownloader = get(),
            vpnController = get(),
            assetPaths = get<XrayAssetPaths>(),
        )
    }
    single { DetailViewmodelFactory(parserFactory = get(), nodeRepository = get()) }
    single {
        SubscriptionViewmodelFactory(
            repository = get(),
            nodeRepository = get(),
        )
    }
    single {
        AppsViewmodelFactory(
            settingsRepo = get<SettingsRepository>(),
            appInfoRepo = get<AppInfoRepository>(),
        )
    }
}
