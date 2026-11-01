package com.android.xrayfa.common.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Completed lookups survive UI cancellation; only actual service responses impose a rate limit. */
class ExitLocationCache(
    private val now: () -> Long,
    private val read: (String) -> Entry?,
    private val write: (String, Entry) -> Unit,
    private val readNextAttempt: () -> Long,
    private val writeNextAttempt: (Long) -> Unit,
    private val readRateLimitUntil: () -> Long,
    private val requestScope: CoroutineScope,
) {
    enum class Failure { CONNECTION, TIMEOUT, HTTP_ERROR, INVALID_RESPONSE, RATE_LIMITED }
    data class Entry(val value: String?, val savedAt: Long, val failure: Failure? = null)
    data class Result(val value: String? = null, val failure: Failure? = null, val retryAt: Long? = null)
    private val mutex = Mutex()

    suspend fun lookup(key: String, fetch: suspend () -> Result): Result = requestScope.async {
        mutex.withLock {
            val time = now()
            read(key)?.let { entry ->
                val lifetime = if (entry.value == null) FAILURE_TTL else SUCCESS_TTL
                if (time - entry.savedAt in 0 until lifetime) {
                    return@withLock Result(entry.value, entry.failure)
                }
            }
            val limitedUntil = readRateLimitUntil()
            if (time < limitedUntil) return@withLock Result(failure = Failure.RATE_LIMITED, retryAt = limitedUntil)
            val nextAttempt = readNextAttempt()
            if (time < nextAttempt) return@withLock Result(retryAt = nextAttempt)
            // Short local spacing protects node switching; this is not a server rate limit.
            writeNextAttempt(time + MIN_REQUEST_INTERVAL)
            val result = fetch()
            if (result.failure != Failure.RATE_LIMITED) {
                write(key, Entry(result.value, now(), result.failure))
            }
            result
        }
    }.await()

    companion object {
        const val SUCCESS_TTL = 30 * 60 * 1_000L
        const val FAILURE_TTL = 60 * 1_000L
        const val MIN_REQUEST_INTERVAL = 15 * 1_000L
    }
}
