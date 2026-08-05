package com.android.xrayfa.network

import com.android.xrayfa.model.SubscriptionMeta
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.isSuccess

data class SubscriptionFetchResult(
    val body: String,
    val meta: SubscriptionMeta,
)

class SubscriptionFetcher(
    private val httpClient: HttpClient,
) {
    suspend fun fetch(url: String, extraHeaders: Map<String, String> = emptyMap()): SubscriptionFetchResult {
        val response = httpClient.get(url) {
            extraHeaders.forEach { (key, value) -> header(key, value) }
        }
        if (!response.status.isSuccess()) {
            throw SubscriptionFetchException("HTTP error: ${response.status.value}")
        }
        val meta = SubscriptionHeaderParser.parseSubscriptionMeta(response.headers.toMultiMap())
        val body = response.body<String>()
        return SubscriptionFetchResult(body = body, meta = meta)
    }

    private fun io.ktor.http.Headers.toMultiMap(): Map<String, List<String>> =
        names().associateWith { name -> getAll(name) ?: emptyList() }
}

class SubscriptionFetchException(message: String) : Exception(message)
