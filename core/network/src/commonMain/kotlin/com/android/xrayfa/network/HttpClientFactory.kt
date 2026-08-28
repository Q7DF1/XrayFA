package com.android.xrayfa.network

import io.ktor.client.HttpClient

expect fun createSubscriptionHttpClient(userAgent: String): HttpClient
