package com.android.xrayfa.network

fun createSubscriptionFetcher(userAgent: String): SubscriptionFetcher {
    return SubscriptionFetcher(createSubscriptionHttpClient(userAgent))
}
