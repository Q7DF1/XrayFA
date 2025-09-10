package com.android.v2rayForAndroidUI.di

import android.content.Context
import hev.htproxy.di.qualifier.Application
import hev.htproxy.di.qualifier.Background
import hev.htproxy.di.qualifier.Main
import hev.htproxy.utils.NetPreferences
import dagger.Binds
import dagger.Module
import dagger.Provides
import hev.htproxy.TProxyService
import hev.htproxy.Tun2SocksService
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import javax.inject.Singleton


@Module(includes = [
    ServiceModule::class,
    ActivityModule::class
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