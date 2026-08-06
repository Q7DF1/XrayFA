package com.android.xrayfa.network

fun createSubscriptionFetcher(userAgent: String): SubscriptionFetcher {
    return SubscriptionFetcher(createSubscriptionHttpClient(userAgent))
}

fun createProxyFileDownloader(
    userAgent: String,
    configProvider: () -> SocksProxyConfig,
): FileDownloader {
    return FileDownloader(createSocksProxyHttpClient(userAgent, configProvider))
}
