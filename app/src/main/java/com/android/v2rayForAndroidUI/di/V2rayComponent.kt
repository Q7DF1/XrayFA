package com.android.v2rayForAndroidUI.di

import android.app.Service
import android.content.Context
import dagger.BindsInstance
import dagger.Component
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
@Component(modules = [GlobalModule::class])
interface V2rayComponent {

    @Component.Builder
    interface Builder {
        @BindsInstance
        fun bindContext(context: Context): Builder

        fun build(): V2rayComponent
    }


    fun getVpnServices(): Map<Class<*>, Provider<Service>>
}