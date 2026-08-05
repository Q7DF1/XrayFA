package com.android.xrayfa.di

import android.content.Context
import com.android.xrayfa.common.di.qualifier.Application
import com.android.xrayfa.common.di.qualifier.Background
import com.android.xrayfa.common.di.qualifier.Main
import dagger.Binds
import dagger.Module
import dagger.Provides
import xrayfa.tun2socks.TProxyService
import xrayfa.tun2socks.Tun2SocksService
import xrayfa.tun2socks.utils.NetPreferences
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import javax.inject.Singleton


@Module(includes = [
    ServiceModule::class,
    ActivityModule::class,
    KoinBridgeModule::class,
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
 }

    @Binds
    abstract fun bindTun2SocksService(service: TProxyService): Tun2SocksService

}