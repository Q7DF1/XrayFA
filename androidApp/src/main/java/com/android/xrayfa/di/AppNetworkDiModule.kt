package com.android.xrayfa.di

import com.android.xrayfa.datastore.SettingsRepository
import com.android.xrayfa.network.FileDownloader
import com.android.xrayfa.network.SocksProxyConfig
import com.android.xrayfa.network.createProxyFileDownloader
import com.android.xrayfa.network.createSubscriptionFetcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

val appNetworkDiModule: Module = module {
    single {
        val context = androidContext()
        val versionName = context.packageManager.getPackageInfo(context.packageName, 0).versionName
        createSubscriptionFetcher(userAgent = "xrayFA/$versionName")
    }

    single<FileDownloader> {
        val context = androidContext()
        val versionName = context.packageManager.getPackageInfo(context.packageName, 0).versionName
        val settingsRepository: SettingsRepository = get()
        createProxyFileDownloader(userAgent = "xrayFA/$versionName") {
            val settings = runBlocking { settingsRepository.settingsFlow.first() }
            SocksProxyConfig(
                socksPort = settings.socksPort,
                socksUserName = settings.socksUserName,
                socksPassword = settings.socksPassword,
            )
        }
    }
}
