package com.android.xrayfa.di

import android.app.Service
import com.android.xrayfa.core.QuickStartTileService
import dagger.Binds
import dagger.Module
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap
import com.android.xrayfa.core.XrayBaseService

/**
 *
 * Define a Service collection and add all the Service that need dependency injection here
 * to complete the dependency injection
 */
@Module
abstract class ServiceModule {

    @Binds
    @IntoMap
    @ClassKey(XrayBaseService::class)
    abstract fun bindVpnService(service: XrayBaseService): Service


    @Binds
    @IntoMap
    @ClassKey(QuickStartTileService::class)
    abstract fun bindTileService(service: QuickStartTileService): Service
}