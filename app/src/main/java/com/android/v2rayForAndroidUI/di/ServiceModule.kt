package com.android.v2rayForAndroidUI.di

import android.app.Service
import dagger.Binds
import dagger.MapKey
import dagger.Module
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap
import hev.htproxy.V2rayVpnService


@Module
abstract class ServiceModule {

    @Binds
    @IntoMap
    @ClassKey(V2rayVpnService::class)
    abstract fun bindVpnService(service: V2rayVpnService): Service
}