package com.android.xrayfa.di

import android.app.Service
import dagger.Binds
import dagger.Module
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap
import com.android.xrayfa.XrayBaseService


@Module
abstract class ServiceModule {

    @Binds
    @IntoMap
    @ClassKey(XrayBaseService::class)
    abstract fun bindVpnService(service: XrayBaseService): Service
}