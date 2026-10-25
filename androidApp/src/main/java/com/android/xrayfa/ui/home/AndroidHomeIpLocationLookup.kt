package com.android.xrayfa.ui.home

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.SystemClock
import com.android.xrayfa.shared.ui.home.HomeMapLocation
import com.android.xrayfa.shared.ui.home.HomeMapLocations
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Looks up the device's real public IP on a non-VPN network and the selected node's IP separately.
 * Both requests are bound to an explicit Android Network so the VPN tunnel cannot turn the device
 * location into the proxy exit location.
 */
class AndroidHomeIpLocationLookup(context: Context) {
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)

    suspend fun lookup(nodeAddress: String?): HomeMapLocations = withContext(Dispatchers.IO) {
        val deviceNetwork = findDirectInternetNetwork()
        val serverNetwork = deviceNetwork ?: connectivityManager.activeNetwork

        val userLocation = deviceNetwork?.let { network ->
            cached("device:${network.networkHandle}") { requestLocation(network, query = null) }
        }
        val serverLocation = nodeAddress
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let { address ->
                serverNetwork?.let { network ->
                    cached("server:${address.lowercase()}") { requestLocation(network, query = address) }
                }
            }

        HomeMapLocations(user = userLocation, server = serverLocation)
    }

    private fun findDirectInternetNetwork(): Network? {
        val candidates = connectivityManager.allNetworks.mapNotNull { network ->
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return@mapNotNull null
            if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ||
                !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            ) {
                return@mapNotNull null
            }
            val rank = when {
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) -> 0
                else -> 1
            }
            Triple(network, capabilities, rank)
        }
        val activeNetwork = connectivityManager.activeNetwork
        return candidates.firstOrNull { it.first == activeNetwork }?.first
            ?: candidates.minByOrNull { it.third }?.first
    }

    private fun requestLocation(network: Network, query: String?): HomeMapLocation? {
        if (IpApiRateLimit.isBlocked()) return null
        val encodedQuery = query?.let { URLEncoder.encode(it, Charsets.UTF_8.name()) }.orEmpty()
        val url = URL("$API_URL$encodedQuery?$FIELDS&lang=zh-CN")
        val connection = network.openConnection(url) as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = READ_TIMEOUT_MILLIS
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", USER_AGENT)

            val responseCode = connection.responseCode
            val remainingRequests = connection.getHeaderField("X-Rl")?.toIntOrNull()
            val retryAfterSeconds = connection.getHeaderField("X-Ttl")?.toLongOrNull()
            if (remainingRequests == 0 && retryAfterSeconds != null) {
                IpApiRateLimit.blockFor(retryAfterSeconds)
            }
            if (responseCode !in 200..299) return null
            val body = connection.inputStream.bufferedReader().use { reader -> reader.readText() }
            if (body.length > MAX_RESPONSE_CHARS) return null
            val json = JSONObject(body)
            if (json.optString("status") != "success") return null

            val latitude = json.optDouble("lat", Double.NaN)
            val longitude = json.optDouble("lon", Double.NaN)
            if (!latitude.isFinite() || !longitude.isFinite() || latitude !in -90.0..90.0 || longitude !in -180.0..180.0) {
                return null
            }

            HomeMapLocation(
                ipAddress = json.optString("query").takeIf(String::isNotBlank) ?: query.orEmpty(),
                city = json.optString("city").takeIf(String::isNotBlank),
                region = json.optString("regionName").takeIf(String::isNotBlank),
                country = json.optString("country").takeIf(String::isNotBlank),
                latitude = latitude,
                longitude = longitude,
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun cached(key: String, load: () -> HomeMapLocation?): HomeMapLocation? {
        val now = SystemClock.elapsedRealtime()
        val cached = LocationCache.get(key, now)
        if (cached != null) return cached.value
        val result = load()
        LocationCache.put(key, result, now + CACHE_TTL_MILLIS)
        return result
    }

    private data class CacheEntry(
        val value: HomeMapLocation?,
        val expiresAtMillis: Long,
    )

    private object LocationCache {
        private val entries = mutableMapOf<String, CacheEntry>()

        @Synchronized
        fun get(key: String, now: Long): CacheEntry? {
            val entry = entries[key] ?: return null
            if (entry.expiresAtMillis <= now) {
                entries.remove(key)
                return null
            }
            return entry
        }

        @Synchronized
        fun put(key: String, value: HomeMapLocation?, expiresAtMillis: Long) {
            entries[key] = CacheEntry(value, expiresAtMillis)
        }
    }

    private object IpApiRateLimit {
        @Volatile
        private var blockedUntilMillis: Long = 0L

        fun isBlocked(): Boolean = SystemClock.elapsedRealtime() < blockedUntilMillis

        fun blockFor(seconds: Long) {
            blockedUntilMillis = maxOf(
                blockedUntilMillis,
                SystemClock.elapsedRealtime() + seconds.coerceAtLeast(1L) * 1_000L,
            )
        }
    }

    private companion object {
        const val API_URL = "http://ip-api.com/json/"
        const val FIELDS = "fields=status,message,query,country,regionName,city,lat,lon"
        const val CONNECT_TIMEOUT_MILLIS = 6_000
        const val READ_TIMEOUT_MILLIS = 6_000
        const val CACHE_TTL_MILLIS = 5 * 60 * 1_000L
        const val MAX_RESPONSE_CHARS = 16_384
        const val USER_AGENT = "XrayFA"
    }
}
