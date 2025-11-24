package com.android.xrayfa.di

import android.content.Context
import com.android.xrayfa.TrafficDetector
import com.android.xrayfa.TrafficDetectorImpl
import com.android.xrayfa.common.di.qualifier.Application
import com.android.xrayfa.dao.LinkDao
import com.android.xrayfa.dao.SubscriptionDao
import com.android.xrayfa.dao.XrayFADatabase
import com.android.xrayfa.parser.SubscriptionParser
import com.android.xrayfa.common.di.qualifier.Background
import com.android.xrayfa.common.di.qualifier.Main
import com.android.xrayfa.dao.NodeDao
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
    NetworkModule::class
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
     @Deprecated("use LinkDao instead")
     fun provideLinkDao(linkDatabase: XrayFADatabase): LinkDao {
         return linkDatabase.LinkDao()
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
     fun provideTrafficDetector(): TrafficDetector {
         return TrafficDetectorImpl()
     }

     @Provides
     @Singleton
     fun provideBase64Parser(): SubscriptionParser {
         return SubscriptionParser()
     }
 }

    @Binds
    abstract fun bindTun2SocksService(service: TProxyService): Tun2SocksService


}