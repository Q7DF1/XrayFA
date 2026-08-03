package com.android.xrayfa.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.android.xrayfa.common.core.GeoIpProvider
import com.android.xrayfa.common.core.TrafficDetector
import com.android.xrayfa.common.core.XrayAssetPaths
import com.android.xrayfa.common.core.XrayCore
import com.android.xrayfa.common.utils.Logger
import com.android.xrayfa.core.AndroidGeoIpProvider
import com.android.xrayfa.core.AndroidLogger
import com.android.xrayfa.core.AndroidXrayAssetPaths
import com.android.xrayfa.core.XrayCoreManager
import com.android.xrayfa.data.settingsDataStore
import com.android.xrayfa.common.di.qualifier.Application
import com.android.xrayfa.dao.SubscriptionDao
import com.android.xrayfa.dao.XrayFADatabase
import com.android.xrayfa.parser.SubscriptionParser
import com.android.xrayfa.common.di.qualifier.Background
import com.android.xrayfa.common.di.qualifier.Main
import com.android.xrayfa.dao.NodeDao
import com.google.gson.Gson
import xrayfa.tun2socks.utils.NetPreferences
import dagger.Binds
import dagger.Module
import dagger.Provides
import xrayfa.tun2socks.TProxyService
import xrayfa.tun2socks.Tun2SocksService
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import javax.inject.Singleton


@Module(includes = [
    ServiceModule::class,
    ActivityModule::class,
    CoroutinesModule::class,
    NetworkModule::class,
    ParserModule::class,
])
abstract class GlobalModule {

 companion object {

     @Provides
     @Application
     fun provideContext(context: Context): Context {
         return context.applicationContext
     }


     @Provides
     @Background
     @Singleton
     fun provideBackgroundExecutor(): Executor {
         return Executors.newSingleThreadExecutor()
     }

     @Provides
     @Main
     @Singleton
     fun provideMainExecutor(context: Context): Executor {
         return context.mainExecutor
     }


     @Provides
     @Singleton
     fun providePreferences(context: Context): NetPreferences {
         return NetPreferences(context)
     }

     @Provides
     @Singleton
     fun provideXrayDatabase(context: Context): XrayFADatabase {
         return XrayFADatabase.getXrayDatabase(context)
     }


     @Provides
     @Singleton
     fun provideNodeDao(xrayFADatabase: XrayFADatabase): NodeDao {
         return xrayFADatabase.NodeDao()
     }

     @Provides
     @Singleton
     fun provideSubscriptionDao(xrayFADatabase: XrayFADatabase): SubscriptionDao {
         return xrayFADatabase.SubscriptionDao()
     }


     @Provides
     @Singleton
     fun provideBase64Parser(): SubscriptionParser {
         return SubscriptionParser()
     }

     @Provides
     @Singleton
     fun provideGson(): Gson {
         return Gson()
     }

     @Provides
     @Singleton
     fun provideSettingsDataStore(@Application context: Context): DataStore<Preferences> {
         return context.settingsDataStore
     }
 }

    @Binds
    abstract fun bindLogger(impl: AndroidLogger): Logger

    @Binds
    abstract fun bindTun2SocksService(service: TProxyService): Tun2SocksService

    @Binds
    abstract fun bindXrayAssetPaths(impl: AndroidXrayAssetPaths): XrayAssetPaths

    @Binds
    abstract fun bindGeoIpProvider(impl: AndroidGeoIpProvider): GeoIpProvider

    @Binds
    abstract fun bindXrayCore(xrayCoreManager: XrayCoreManager): XrayCore

    @Binds
    abstract fun bindTrafficDetector(xrayCoreManager: XrayCoreManager): TrafficDetector


}