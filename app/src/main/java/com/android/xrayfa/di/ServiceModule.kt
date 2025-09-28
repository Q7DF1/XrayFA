package com.android.xrayfa.di

import android.app.Service
import dagger.Binds
import dagger.Module
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap
import com.android.xrayfa.V2rayBaseService


@Module
abstract class ServiceModule {

    @Binds
    @IntoMap
    @ClassKey(V2rayBaseService::class)
    abstract fun bindVpnService(service: V2rayBaseService): Service
}