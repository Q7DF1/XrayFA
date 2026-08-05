package com.android.xrayfa.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HttpHeaders
import java.util.concurrent.TimeUnit

actual fun createSubscriptionHttpClient(userAgent: String): HttpClient {
    return HttpClient(OkHttp) {
        engine {
            config {
                connectTimeout(30, TimeUnit.SECONDS)
                readTimeout(30, TimeUnit.SECONDS)
                writeTimeout(30, TimeUnit.SECONDS)
                retryOnConnectionFailure(true)
            }
        }
        defaultRequest {
            headers.append(HttpHeaders.Accept, "*/*")
            headers.append(HttpHeaders.UserAgent, userAgent)
        }
    }
}
