package com.android.xrayfa.di

import com.android.xrayfa.database.AndroidXrayDatabaseFactory
import com.android.xrayfa.database.dao.NodeDao
import com.android.xrayfa.database.dao.SubscriptionDao
import com.android.xrayfa.parser.SubscriptionParser
import com.android.xrayfa.repository.AndroidSubscriptionRepository
import com.android.xrayfa.repository.NodeRepository
import com.android.xrayfa.repository.RoomNodeRepository
import com.android.xrayfa.repository.SubscriptionRepository
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

val appDataDiModule: Module = module {
    single<NodeDao> { AndroidXrayDatabaseFactory.getNodeDao(androidContext()) }
    single<SubscriptionDao> { AndroidXrayDatabaseFactory.getSubscriptionDao(androidContext()) }
    single { SubscriptionParser() }
    single<NodeRepository> { RoomNodeRepository(get()) }
    single<SubscriptionRepository> {
        AndroidSubscriptionRepository(
            subscriptionDao = get(),
            subscriptionFetcher = get(),
            nodeRepository = get(),
            subscriptionParser = get(),
            parserFactory = get(),
            settingsRepository = get(),
        )
    }
}

/** Koin qualifier names for coroutine scopes. */
object KoinQualifiers {
    const val MAIN_SCOPE = "MainScope"
    const val BACKGROUND_SCOPE = "BackgroundScope"
}
