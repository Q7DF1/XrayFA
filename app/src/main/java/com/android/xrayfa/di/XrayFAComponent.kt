package com.android.xrayfa.di

import android.app.Activity
import android.app.Service
import android.content.Context
import com.android.xrayfa.XrayAppCompatFactory
import dagger.BindsInstance
import dagger.Component
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
@Component(modules = [GlobalModule::class])
interface XrayFAComponent {

    @Component.Builder
    interface Builder {
        @BindsInstance
        fun bindContext(context: Context): Builder

        fun build(): XrayFAComponent
    }


    fun getVpnServices(): Map<Class<*>, Provider<Service>>
    fun getActivities(): Map<Class<*>, Provider<Activity>>



    fun inject(appCompatFactory: XrayAppCompatFactory)
}