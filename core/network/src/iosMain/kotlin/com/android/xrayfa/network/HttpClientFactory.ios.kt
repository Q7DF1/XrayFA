package com.android.xrayfa.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HttpHeaders

actual fun createSubscriptionHttpClient(userAgent: String): HttpClient {
    return HttpClient(Darwin) {
        engine {
            configureRequest {
                setTimeoutInterval(30.0)
            }
        }
        defaultRequest {
            headers.append(HttpHeaders.Accept, "*/*")
            headers.append(HttpHeaders.UserAgent, userAgent)
        }
    }
}
