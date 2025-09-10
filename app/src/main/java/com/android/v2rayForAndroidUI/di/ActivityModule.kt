package com.android.v2rayForAndroidUI.di

import android.app.Activity
import com.android.v2rayForAndroidUI.MainActivity
import dagger.Binds
import dagger.Module
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap


@Module
abstract class ActivityModule {

    @Binds
    @IntoMap
    @ClassKey(MainActivity::class)
    abstract fun bindMainActivity(activity: MainActivity): Activity

}