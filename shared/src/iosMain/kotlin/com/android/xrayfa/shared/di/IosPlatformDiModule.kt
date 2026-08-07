package com.android.xrayfa.shared.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.android.xrayfa.common.core.GeoIpProvider
import com.android.xrayfa.common.core.XrayAssetPaths
import com.android.xrayfa.common.repository.ConfigParserSettingsProvider
import com.android.xrayfa.common.utils.Logger
import com.android.xrayfa.datastore.SettingsDataStoreContext
import com.android.xrayfa.datastore.SettingsRepository
import com.android.xrayfa.datastore.createSettingsDataStore
import com.android.xrayfa.shared.platform.IosGeoIpProvider
import com.android.xrayfa.shared.platform.IosLogger
import com.android.xrayfa.shared.platform.IosXrayAssetPaths
import com.android.xrayfa.shared.vpn.IosTrafficStatsSource
import com.android.xrayfa.shared.vpn.IosVpnConnectCoordinator
import com.android.xrayfa.shared.vpn.TrafficStatsSource
import com.android.xrayfa.shared.vpn.VpnConnectCoordinator
import com.android.xrayfa.vpn.IosVpnController
import com.android.xrayfa.vpn.VpnController
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** iOS platform Koin bindings (subset of Android [appPlatformDiModule]). */
val iosPlatformDiModule: Module = module {
    single<Logger> { IosLogger() }
    single { SettingsDataStoreContext() }
    single<DataStore<Preferences>> { createSettingsDataStore(get()) }
    single { SettingsRepository(get(), get()) }
    single<ConfigParserSettingsProvider> { get<SettingsRepository>() }
    single<XrayAssetPaths> { IosXrayAssetPaths() }
    single<GeoIpProvider> { IosGeoIpProvider() }
    single {
        IosVpnController(scope = get(named(KoinQualifiers.MAIN_SCOPE)))
    }
    single<VpnController> { get<IosVpnController>() }
    single<VpnConnectCoordinator> {
        IosVpnConnectCoordinator(
            vpnController = get(),
            parserFactory = get(),
            startOptionsResolver = get(),
        )
    }
    single<TrafficStatsSource> {
        IosTrafficStatsSource(scope = get(named(KoinQualifiers.BACKGROUND_SCOPE)))
    }
}
