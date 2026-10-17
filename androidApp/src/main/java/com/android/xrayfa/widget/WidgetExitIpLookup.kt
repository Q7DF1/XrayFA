package com.android.xrayfa.widget

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import java.net.Inet6Address
import java.net.InetAddress

/** Looks up the public IP through the app's local Xray SOCKS inbound. */
class WidgetExitIpLookup(private val client: HttpClient) {
    suspend fun lookup(): String {
        val response = client.get(EXIT_IP_URL)
        check(response.status.isSuccess()) { "Exit IP service returned HTTP ${response.status.value}" }
        return response.bodyAsText().trim().takeIf(::isIpAddress)
            ?: error("Exit IP service returned an invalid address")
    }

    private fun isIpAddress(value: String): Boolean {
        if (value.isEmpty() || value.any { it.isWhitespace() }) return false
        if (value.contains(':')) {
            return runCatching { InetAddress.getByName(value) is Inet6Address }.getOrDefault(false)
        }
        val octets = value.split('.')
        return octets.size == 4 && octets.all { part ->
            part.isNotEmpty() && part.all(Char::isDigit) && part.toIntOrNull()?.let { it in 0..255 } == true
        }
    }

    private companion object {
        const val EXIT_IP_URL = "https://api.ipify.org"
    }
}
