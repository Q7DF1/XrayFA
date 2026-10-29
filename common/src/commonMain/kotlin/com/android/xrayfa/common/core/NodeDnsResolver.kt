package com.android.xrayfa.common.core

import com.android.xrayfa.common.config.DnsDefaults
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Resolves map node names using the selected DNS transport and configured upstreams. */
class NodeDnsResolver(
    private val exchange: suspend (mode: Mode, endpoint: String, query: ByteArray) -> Response,
    private val now: () -> Long,
    private val scope: CoroutineScope,
) {
    enum class Mode { DOH, PLAIN }
    data class Response(val bytes: ByteArray, val ageSeconds: Long = 0)
    private data class Key(val host: String, val endpoints: List<String>, val ipv6: Boolean, val session: Long, val mode: Mode)
    private data class Cached(val addresses: List<String>, val expiresAt: Long)
    private val cache = mutableMapOf<Key, Cached>()
    private val mutex = Mutex()

    suspend fun resolve(host: String, endpoints: List<String>, ipv6: Boolean, session: Long, mode: Mode = Mode.DOH): List<String> {
        if (parseIpLiteral(host) != null) return listOf(host)
        require(endpoints.isNotEmpty() && endpoints.all {
            if (mode == Mode.DOH) DnsDefaults.isValidDohServer(it) else DnsDefaults.isValidIpAddress(it)
        })
        val key = Key(host.trimEnd('.').lowercase(), endpoints.toList(), ipv6, session, mode)
        return scope.async {
            mutex.withLock {
                cache[key]?.takeIf { now() < it.expiresAt }?.let { return@withLock it.addresses }
                var ttl = 900L
                val addresses = buildList {
                    for (type in if (ipv6) listOf(DohDnsMessage.A, DohDnsMessage.AAAA) else listOf(DohDnsMessage.A)) {
                        for (endpoint in endpoints) {
                            try {
                                val resolved = withTimeoutOrNull(5_000L) {
                                    var name = key.host
                                    var answer: DohDnsMessage.Answer
                                    val seen = mutableSetOf<String>()
                                    do {
                                        check(seen.size < 8 && seen.add(name))
                                        val response = exchange(mode, endpoint, DohDnsMessage.query(name, type))
                                        answer = DohDnsMessage.answer(response.bytes, name, type)
                                        ttl = minOf(ttl, (answer.ttlSeconds - response.ageSeconds.coerceAtLeast(0)).coerceAtLeast(0))
                                        name = answer.canonicalName ?: name
                                    } while (answer.addresses.isEmpty() && answer.canonicalName != null)
                                    answer.addresses
                                }.orEmpty()
                                if (resolved.isNotEmpty()) {
                                    addAll(resolved)
                                    break
                                }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                // Try another configured upstream within the selected transport.
                            }
                        }
                    }
                }.distinct()
                if (cache.size >= 64) cache.remove(cache.keys.first())
                cache[key] = Cached(addresses, now() + if (addresses.isEmpty()) 30_000L else ttl * 1_000L)
                addresses
            }
        }.await()
    }
}
