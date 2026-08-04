package com.android.xrayfa.di

import com.android.xrayfa.dao.NodeDao
import com.android.xrayfa.dao.SubscriptionDao
import com.android.xrayfa.dao.XrayFADatabase
import com.android.xrayfa.parser.SubscriptionParser
import com.android.xrayfa.repository.NodeRepository
import com.android.xrayfa.repository.SubscriptionRepository
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

val appDataDiModule: Module = module {
    single { XrayFADatabase.getXrayDatabase(androidContext()) }
    single<NodeDao> { get<XrayFADatabase>().NodeDao() }
    single<SubscriptionDao> { get<XrayFADatabase>().SubscriptionDao() }
    single { SubscriptionParser() }
    single { NodeRepository(get()) }
    single {
        SubscriptionRepository(
            subscriptionDao = get(),
            okHttp = get(named(KoinQualifiers.SHORT_TIME)),
            nodeRepository = get(),
            subscriptionParser = get(),
            parserFactory = get(),
            settingsRepository = get(),
        )
    }
}

/** Koin qualifier names aligned with Dagger [@ShortTime] / [@LongTime]. */
object KoinQualifiers {
    const val SHORT_TIME = "ShortTime"
    const val LONG_TIME = "LongTime"
}
