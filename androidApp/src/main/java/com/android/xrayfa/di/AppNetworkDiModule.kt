package com.android.xrayfa.di

import com.android.xrayfa.datastore.SettingsRepository
import com.android.xrayfa.network.FileDownloader
import com.android.xrayfa.network.SocksProxyConfig
import com.android.xrayfa.network.createSocksProxyHttpClient
import com.android.xrayfa.network.createProxyFileDownloader
import com.android.xrayfa.network.createSubscriptionFetcher
import com.android.xrayfa.widget.WidgetExitIpLookup
import com.android.xrayfa.ui.home.AndroidHomeIpLocationLookup
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

private const val WIDGET_EXIT_IP_HTTP_CLIENT = "widgetExitIpHttpClient"

val appNetworkDiModule: Module = module {
    single {
        AndroidHomeIpLocationLookup(
            androidContext(),
            get(named(WIDGET_EXIT_IP_HTTP_CLIENT)),
        )
    }

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

    single<HttpClient>(named(WIDGET_EXIT_IP_HTTP_CLIENT)) {
        val context = androidContext()
        val versionName = context.packageManager.getPackageInfo(context.packageName, 0).versionName
        val settingsRepository: SettingsRepository = get()
        createSocksProxyHttpClient(userAgent = "xrayFA/${versionName ?: "unknown"}") {
            val settings = runBlocking { settingsRepository.settingsFlow.first() }
            SocksProxyConfig(settings.socksPort, settings.socksUserName, settings.socksPassword)
        }
    }

    single { WidgetExitIpLookup(get(named(WIDGET_EXIT_IP_HTTP_CLIENT))) }
}
