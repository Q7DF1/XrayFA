package com.android.xrayfa.ui.home

import android.content.Context
import android.util.Log
import com.android.xrayfa.core.XrayBaseService
import com.android.xrayfa.shared.ui.home.HomeMapIssue
import com.android.xrayfa.common.core.ExitLocationCache
import com.android.xrayfa.common.core.parseIpLiteral
import com.android.xrayfa.shared.ui.home.HomeMapLocation
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.MessageDigest

/** This client uses only the local SOCKS inbound; there is no direct-network fallback. */
class AndroidExitLocationLookup(context: Context, private val client: HttpClient) {
    private val preferences = context.getSharedPreferences("exit_location_cache_v2", Context.MODE_PRIVATE)
    private val requestScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cache = ExitLocationCache(
        now = System::currentTimeMillis,
        read = { key ->
            if (preferences.contains("time_$key")) ExitLocationCache.Entry(
                preferences.getString("data_$key", null), preferences.getLong("time_$key", 0),
                preferences.getString("failure_$key", null)?.let { name ->
                    ExitLocationCache.Failure.entries.firstOrNull { it.name == name }
                },
            ) else null
        },
        write = { key, entry ->
            // Bound persistent cache size. Expired entries are harmless and replaced on lookup.
            val times = preferences.all.filterKeys { it.startsWith("time_") }
            val editor = preferences.edit()
            if (times.size >= 32 && !times.containsKey("time_$key")) {
                times.minByOrNull { (it.value as? Long) ?: 0L }?.key?.let {
                    editor.remove(it).remove(it.replaceFirst("time_", "data_")).remove(it.replaceFirst("time_", "failure_"))
                }
            }
            editor.putLong("time_$key", entry.savedAt).putString("data_$key", entry.value)
                .putString("failure_$key", entry.failure?.name).apply()
        },
        readNextAttempt = { preferences.getLong("next_attempt", 0) },
        writeNextAttempt = { preferences.edit().putLong("next_attempt", it).apply() },
        readRateLimitUntil = { preferences.getLong("rate_limit_until", 0) },
        requestScope = requestScope,
    )

    data class Result(
        val location: HomeMapLocation? = null,
        val issue: HomeMapIssue? = null,
        val nextLookupDelayMillis: Long = 60_000L,
    )

    suspend fun lookup(identity: String, sessionId: Long): Result {
        val key = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val result = cache.lookup(key) {
            try {
                withTimeoutOrNull(10_000) {
                    if (XrayBaseService.activeSession.value?.sessionId != sessionId) {
                        throw CancellationException("VPN session changed")
                    }
                    val response = client.get(URL)
                    if (XrayBaseService.activeSession.value?.sessionId != sessionId) {
                        throw CancellationException("VPN session changed")
                    }
                    val limited = response.status.value == 429
                    if (limited || response.headers["X-Rl"] == "0") {
                        val seconds = response.headers["X-Ttl"]?.toLongOrNull()?.coerceIn(1L, 86_400L) ?: 60L
                        preferences.edit().putLong(
                            "rate_limit_until", System.currentTimeMillis() + seconds * 1_000L,
                        ).apply()
                    }
                    when {
                        limited -> ExitLocationCache.Result(failure = ExitLocationCache.Failure.RATE_LIMITED)
                        !response.status.isSuccess() -> {
                            Log.w(TAG, "Exit location request failed: HTTP ${response.status.value}")
                            ExitLocationCache.Result(failure = ExitLocationCache.Failure.HTTP_ERROR)
                        }
                        else -> {
                            val payload = response.bodyAsText()
                            if (runCatching { parse(payload) }.getOrNull() != null) {
                                ExitLocationCache.Result(value = payload)
                            } else {
                                Log.w(TAG, "Exit location service returned an invalid location")
                                ExitLocationCache.Result(failure = ExitLocationCache.Failure.INVALID_RESPONSE)
                            }
                        }
                    }
                } ?: ExitLocationCache.Result(failure = ExitLocationCache.Failure.TIMEOUT)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w(TAG, "Exit location connection failed: ${error.javaClass.simpleName}")
                ExitLocationCache.Result(failure = ExitLocationCache.Failure.CONNECTION)
            }
        }
        val location = result.value?.let { runCatching { parse(it) }.getOrNull() }
        val issue = if (location != null) null else when (result.failure) {
            ExitLocationCache.Failure.RATE_LIMITED -> HomeMapIssue.EXIT_RATE_LIMITED
            ExitLocationCache.Failure.TIMEOUT -> HomeMapIssue.EXIT_TIMEOUT
            ExitLocationCache.Failure.INVALID_RESPONSE, ExitLocationCache.Failure.HTTP_ERROR ->
                HomeMapIssue.EXIT_SERVICE_ERROR
            ExitLocationCache.Failure.CONNECTION -> HomeMapIssue.EXIT_UNAVAILABLE
            null -> null // A short local cooldown is retried silently, without claiming server throttling.
        }
        return Result(
            location, issue,
            result.retryAt?.let { (it - System.currentTimeMillis()).coerceIn(1_000L, 60_000L) } ?: 60_000L,
        )
    }

    private fun parse(payload: String): HomeMapLocation? {
        val data = Json.parseToJsonElement(payload).jsonObject
        fun text(name: String) = data[name]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
        if (text("status") != "success") return null
        val ip = text("query")?.takeIf { parseIpLiteral(it) != null } ?: return null
        val lat = data["lat"]?.jsonPrimitive?.doubleOrNull ?: return null
        val lon = data["lon"]?.jsonPrimitive?.doubleOrNull ?: return null
        if (!lat.isFinite() || !lon.isFinite() || lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        return HomeMapLocation(ip, text("city"), text("regionName"), text("country"), lat, lon)
    }

    private companion object {
        const val TAG = "ExitLocationLookup"
        const val URL = "http://ip-api.com/json/?fields=status,query,country,regionName,city,lat,lon"
    }
}
