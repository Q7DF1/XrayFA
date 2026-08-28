package com.android.xrayfa.shared.navigation

import com.arkivanov.decompose.ComponentContext
import org.koin.mp.KoinPlatform

typealias SubscriptionComponentFactory = (ComponentContext) -> SubscriptionComponent

fun defaultSubscriptionComponentFactory(): SubscriptionComponentFactory =
    { componentContext ->
        val koin = KoinPlatform.getKoin()
        DefaultSubscriptionComponent(
            componentContext = componentContext,
            subscriptionRepository = koin.get(),
            nodeRepository = koin.get(),
        )
    }
