package com.android.xrayfa.di

import com.android.xrayfa.common.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.io.IOException
import java.net.Authenticator
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.util.concurrent.TimeUnit

val appNetworkDiModule: Module = module {
    single {
        val context = androidContext()
        val versionName = context.packageManager.getPackageInfo(context.packageName, 0).versionName
        Interceptor { chain ->
            val original = chain.request()
            val request = original.newBuilder()
                .header("Accept", "*/*")
                .header("User-Agent", "xrayFA/$versionName")
                .method(original.method, original.body)
                .build()
            chain.proceed(request)
        }
    }

    single(named(KoinQualifiers.SHORT_TIME)) {
        OkHttpClient.Builder()
            .addInterceptor(get<Interceptor>())
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    single(named(KoinQualifiers.LONG_TIME)) {
        val settingsRepository: SettingsRepository = get()
        Authenticator.setDefault(object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication? {
                val currentSettings = runBlocking { settingsRepository.settingsFlow.first() }
                return if (requestingHost == "127.0.0.1" && currentSettings.socksUserName.isNotEmpty()) {
                    PasswordAuthentication(
                        currentSettings.socksUserName,
                        currentSettings.socksPassword.toCharArray(),
                    )
                } else {
                    null
                }
            }
        })

        OkHttpClient.Builder()
            .addInterceptor(get<Interceptor>())
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .proxySelector(object : ProxySelector() {
                override fun select(uri: URI?): List<Proxy> {
                    val settings = runBlocking { settingsRepository.settingsFlow.first() }
                    return listOf(
                        Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", settings.socksPort)),
                    )
                }

                override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) = Unit
            })
            .build()
    }
}
