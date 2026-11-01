package com.android.xrayfa.common.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ExitLocationCacheTest {
    private var time = 1_000L
    private var nextAttempt = 0L
    private var rateLimitUntil = 0L
    private val entries = mutableMapOf<String, ExitLocationCache.Entry>()
    private fun cache() = ExitLocationCache(
        { time }, entries::get, { key, entry -> entries[key] = entry },
        { nextAttempt }, { nextAttempt = it }, { rateLimitUntil },
        CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
    )
    private fun success(value: String) = ExitLocationCache.Result(value = value)

    @Test
    fun resultsSurviveRecreationAndExpireAfterThirtyMinutes() = runBlocking {
        assertEquals("first", cache().lookup("node") { success("first") }.value)
        time += ExitLocationCache.SUCCESS_TTL - 1
        assertEquals("first", cache().lookup("node") { error("Cache missed") }.value)
        time++
        assertEquals("updated", cache().lookup("node") { success("updated") }.value)
    }

    @Test
    fun connectionFailuresAreCachedForOneMinuteAndAreNotReportedAsRateLimits() = runBlocking {
        val failed = ExitLocationCache.Result(failure = ExitLocationCache.Failure.CONNECTION)
        assertEquals(failed, cache().lookup("one") { failed })
        time += ExitLocationCache.FAILURE_TTL - 1
        assertEquals(failed, cache().lookup("one") { error("Retried too early") })
        time++
        assertEquals("ok", cache().lookup("one") { success("ok") }.value)
    }

    @Test
    fun switchingNodesUsesShortLocalSpacingWithoutClaimingServerThrottling() = runBlocking {
        cache().lookup("one") { success("one") }
        val waiting = cache().lookup("two") { error("Too soon") }
        assertNull(waiting.failure)
        assertEquals(time + ExitLocationCache.MIN_REQUEST_INTERVAL, waiting.retryAt)
        time += ExitLocationCache.MIN_REQUEST_INTERVAL
        assertEquals("two", cache().lookup("two") { success("two") }.value)
    }

    @Test
    fun concurrentLookupsShareOneRequest() = runBlocking {
        val cache = cache()
        var calls = 0
        val results = List(8) {
            async { cache.lookup("node") { calls++; delay(10); success("result") } }
        }.awaitAll()
        assertEquals(1, calls)
        assertEquals(List(8) { success("result") }, results)
    }

    @Test
    fun leavingPageDoesNotCancelLookupOrLoseResultOnReentry() = runBlocking {
        val cache = cache()
        val started = CompletableDeferred<Unit>()
        val response = CompletableDeferred<Unit>()
        var calls = 0
        val leavingPage = async {
            cache.lookup("node") { calls++; started.complete(Unit); response.await(); success("exit") }
        }
        started.await()
        leavingPage.cancel()
        response.complete(Unit)
        assertEquals("exit", cache.lookup("node") { error("Original request was lost") }.value)
        assertEquals(1, calls)
    }

    @Test
    fun actualRateLimitHonorsExpiryAndStillAllowsCachedResults() = runBlocking {
        val cache = cache()
        cache.lookup("cached") { success("exit") }
        time += ExitLocationCache.MIN_REQUEST_INTERVAL
        rateLimitUntil = time + 45_000
        assertEquals("exit", cache.lookup("cached") { error("Cache missed") }.value)
        assertEquals(ExitLocationCache.Failure.RATE_LIMITED, cache.lookup("new") { error("Service throttled") }.failure)
        time = rateLimitUntil
        assertEquals("ok", cache.lookup("new") { success("ok") }.value)
    }
}
