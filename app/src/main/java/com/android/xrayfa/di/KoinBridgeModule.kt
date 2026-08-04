package com.android.xrayfa.di

import com.android.xrayfa.parser.ParserFactory
import com.android.xrayfa.repository.NodeRepository
import com.android.xrayfa.repository.SubscriptionRepository
import dagger.Module
import dagger.Provides
import org.koin.core.context.GlobalContext
import javax.inject.Singleton

/**
 * Bridges Koin-resolved types into the Dagger graph during the D.1 transition.
 * Remove this module once app components resolve dependencies from Koin directly.
 */
@Module
object KoinBridgeModule {

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
