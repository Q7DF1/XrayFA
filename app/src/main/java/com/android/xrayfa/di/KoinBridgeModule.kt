package com.android.xrayfa.di

import com.android.xrayfa.common.di.qualifier.LongTime
import com.android.xrayfa.common.di.qualifier.ShortTime
import com.android.xrayfa.common.repository.SettingsRepository
import com.android.xrayfa.parser.ParserFactory
import com.android.xrayfa.parser.SubscriptionParser
import com.android.xrayfa.repository.NodeRepository
import com.android.xrayfa.repository.SubscriptionRepository
import dagger.Module
import dagger.Provides
import okhttp3.OkHttpClient
import org.koin.core.context.GlobalContext
import org.koin.core.qualifier.named
import javax.inject.Singleton

/**
 * Bridges Koin-resolved types into the Dagger graph during the D.1 transition.
 * Remove this module once app components resolve dependencies from Koin directly.
 */
@Module
object KoinBridgeModule {

    @Provides
    @Singleton
    fun provideSettingsRepository(): SettingsRepository = GlobalContext.get().get()

    @Provides
    @Singleton
    fun provideSubscriptionParser(): SubscriptionParser = GlobalContext.get().get()

    @Provides
    @Singleton
    @ShortTime
    fun provideShortTimeOkHttpClient(): OkHttpClient =
        GlobalContext.get().get(named(KoinQualifiers.SHORT_TIME))

    @Provides
    @Singleton
    @LongTime
    fun provideLongTimeOkHttpClient(): OkHttpClient =
        GlobalContext.get().get(named(KoinQualifiers.LONG_TIME))

    @Provides
    @Singleton
    fun provideParserFactory(): ParserFactory = GlobalContext.get().get()

    @Provides
    @Singleton
    fun provideNodeRepository(): NodeRepository = GlobalContext.get().get()

    @Provides
    @Singleton
    fun provideSubscriptionRepository(): SubscriptionRepository = GlobalContext.get().get()
}
