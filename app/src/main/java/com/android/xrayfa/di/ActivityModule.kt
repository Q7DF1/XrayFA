package com.android.xrayfa.di

import android.app.Activity
import com.android.xrayfa.MainActivity
import com.android.xrayfa.ui.AppsActivity
import com.android.xrayfa.ui.DetailActivity
import com.android.xrayfa.ui.QRCodeActivity
import com.android.xrayfa.ui.SettingsActivity
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


    @Binds
    @IntoMap
    @ClassKey(QRCodeActivity::class)
    abstract fun bindQRCodeActivity(activity: QRCodeActivity): Activity

    @Binds
    @IntoMap
    @ClassKey(DetailActivity::class)
    abstract fun bindDetailActivity(activity: DetailActivity): Activity

    @Binds
    @IntoMap
    @ClassKey(SettingsActivity::class)
    abstract fun bindSettingsActivity(activity: SettingsActivity): Activity

    @Binds
    @IntoMap
    @ClassKey(AppsActivity::class)
    abstract fun bindAppsActivity(activity: AppsActivity): Activity
}