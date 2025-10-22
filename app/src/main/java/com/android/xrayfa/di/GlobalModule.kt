package com.android.xrayfa.di

import android.content.Context
import com.android.xrayfa.TrafficDetector
import com.android.xrayfa.TrafficDetectorImpl
import com.android.xrayfa.dao.LinkDao
import com.android.xrayfa.dao.LinkDatabase
import xrayfa.tun2socks.qualifier.Application
import xrayfa.tun2socks.qualifier.Background
import xrayfa.tun2socks.qualifier.Main
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
    CoroutinesModule::class
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
     fun provideLinkDatabase(context: Context): LinkDatabase {
         return LinkDatabase.getLinkDatabase(context)
     }

     @Provides
     @Singleton
     fun provideLinkDao(linkDatabase: LinkDatabase): LinkDao {
         return linkDatabase.LinkDao()
     }

     @Provides
     @Singleton
     fun provideTrafficDetector(): TrafficDetector {
         return TrafficDetectorImpl()
     }
 }

    @Binds
    abstract fun bindTun2SocksService(service: TProxyService): Tun2SocksService


}