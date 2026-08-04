package com.android.xrayfa.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.android.xrayfa.common.core.GeoIpProvider
import com.android.xrayfa.common.core.XrayAssetPaths
import com.android.xrayfa.common.repository.ConfigParserSettingsProvider
import com.android.xrayfa.common.repository.SettingsRepository
import com.android.xrayfa.common.utils.Logger
import com.android.xrayfa.core.AndroidGeoIpProvider
import com.android.xrayfa.core.AndroidLogger
import com.android.xrayfa.core.AndroidXrayAssetPaths
import com.android.xrayfa.data.settingsDataStore
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Android platform bindings for shared Koin modules.
 * Dagger remains the runtime DI for app components during the D.1 transition.
 */
val appPlatformDiModule: Module = module {
    single<Logger> { AndroidLogger() }
    single<DataStore<Preferences>> { androidContext().settingsDataStore }
    single { SettingsRepository(get(), get()) }
    single<ConfigParserSettingsProvider> { get<SettingsRepository>() }
    single<XrayAssetPaths> { AndroidXrayAssetPaths(androidContext()) }
    single<GeoIpProvider> { AndroidGeoIpProvider(get()) }
}

fun androidKoinModules(): List<Module> = listOf(
    appPlatformDiModule,
    appNetworkDiModule,
    appDataDiModule,
    androidDomainDiModule,
    parserDiModule(),
)
