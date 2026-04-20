package com.android.xrayfa.di

import android.app.Activity
import com.android.xrayfa.MainActivity
import dagger.Binds
import dagger.Module
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap

/**
 *
 * Define a collection of Activities and add all Activities that require dependency injection here
 * to complete dependency injection
 */
@Module
abstract class ActivityModule {

    @Binds
    @IntoMap
    @ClassKey(MainActivity::class)
    abstract fun bindMainActivity(activity: MainActivity): Activity
}