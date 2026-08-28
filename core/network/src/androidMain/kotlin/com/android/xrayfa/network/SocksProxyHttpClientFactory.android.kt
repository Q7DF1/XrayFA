package com.android.xrayfa.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HttpHeaders
import java.io.IOException
import java.net.Authenticator
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.util.concurrent.TimeUnit

fun createSocksProxyHttpClient(
    userAgent: String,
    configProvider: () -> SocksProxyConfig,
): HttpClient {
    Authenticator.setDefault(object : Authenticator() {
        override fun getPasswordAuthentication(): PasswordAuthentication? {
            val config = configProvider()
            return if (requestingHost == "127.0.0.1" && config.socksUserName.isNotEmpty()) {
                PasswordAuthentication(
                    config.socksUserName,
                    config.socksPassword.toCharArray(),
                )
            } else {
                null
            }
        }
    })

    return HttpClient(OkHttp) {
        engine {
            config {
                connectTimeout(30, TimeUnit.SECONDS)
                readTimeout(120, TimeUnit.SECONDS)
                writeTimeout(120, TimeUnit.SECONDS)
                retryOnConnectionFailure(false)
                proxySelector(object : ProxySelector() {
                    override fun select(uri: URI?): List<Proxy> {
                        val config = configProvider()
                        return listOf(
                            Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", config.socksPort)),
                        )
                    }

                    override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) = Unit
                })
            }
        }
        defaultRequest {
            headers.append(HttpHeaders.Accept, "*/*")
            headers.append(HttpHeaders.UserAgent, userAgent)
        }
    }
}
