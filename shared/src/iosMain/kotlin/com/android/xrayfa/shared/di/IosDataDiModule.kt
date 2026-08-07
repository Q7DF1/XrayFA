package com.android.xrayfa.shared.di

import com.android.xrayfa.database.IosXrayDatabaseFactory
import com.android.xrayfa.database.dao.NodeDao
import com.android.xrayfa.database.dao.SubscriptionDao
import com.android.xrayfa.parser.SubscriptionParser
import com.android.xrayfa.repository.KmpSubscriptionRepository
import com.android.xrayfa.repository.NodeRepository
import com.android.xrayfa.repository.RoomNodeRepository
import com.android.xrayfa.repository.SubscriptionRepository
import com.android.xrayfa.shared.vpn.VpnStartOptionsResolver
import org.koin.core.module.Module
import org.koin.dsl.module

val iosDataDiModule: Module = module {
    single<NodeDao> { IosXrayDatabaseFactory.getNodeDao() }
    single<SubscriptionDao> { IosXrayDatabaseFactory.getSubscriptionDao() }
    single { SubscriptionParser() }
    single<NodeRepository> { RoomNodeRepository(get()) }
    single<SubscriptionRepository> {
        KmpSubscriptionRepository(
            subscriptionDao = get(),
            subscriptionFetcher = get(),
            nodeRepository = get(),
            subscriptionParser = get(),
            parserFactory = get(),
            settingsRepository = get(),
            logger = get(),
        )
    }
    single { VpnStartOptionsResolver(get(), get()) }
}
