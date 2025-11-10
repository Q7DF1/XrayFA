package com.android.xrayfa.di

import android.content.Context
import com.android.xrayfa.common.di.qualifier.Application
import com.android.xrayfa.common.di.qualifier.LongTime
import com.android.xrayfa.common.di.qualifier.ShortTime
import dagger.Module
import dagger.Provides
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import javax.inject.Singleton


@Module
class NetworkModule {
    @Provides
    fun provideInterceptor(
        @Application context: Context
    ): Interceptor {
        val versionName = context.packageManager.getPackageInfo(context.packageName, 0).versionName
        return Interceptor { chain ->
            val original = chain.request()
            val request = original.newBuilder()
                .header("Accept","*/*")
                .header("User-Agent", "xrayFA/$versionName")
                .method(original.method, original.body)
                .build()
            chain.proceed(request)
        }
    }


    @Provides
    @ShortTime
    fun provideOkHttpClient(interceptor: Interceptor): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    @Provides
    @LongTime
    fun provideDownloadHttpClient(interceptor: Interceptor): OkHttpClient {
        val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", 10808))
        return OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .proxy(proxy)
            .build()
    }
}