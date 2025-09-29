package com.android.xrayfa.di

import android.app.Activity
import com.android.xrayfa.MainActivity
import com.android.xrayfa.ui.DetailActivity
import com.android.xrayfa.ui.QRCodeActivity
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


    @Binds
    @IntoMap
    @ClassKey(QRCodeActivity::class)
    abstract fun bindQRCodeActivity(activity: QRCodeActivity): Activity

    @Binds
    @IntoMap
    @ClassKey(DetailActivity::class)
    abstract fun bindDetailActivity(activity: DetailActivity): Activity
}