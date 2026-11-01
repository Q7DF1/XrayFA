package com.android.xrayfa.common.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class NodeDnsResolverTest {
    private val host = "node.example"
    private val endpoints = listOf("https://one.example/dns-query", "https://two.example/dns-query")
    private var time = 0L
    private fun resolver(exchange: suspend (NodeDnsResolver.Mode, String, ByteArray) -> NodeDnsResolver.Response) = NodeDnsResolver(
        exchange, { time }, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
    )
    private fun name(value: String) = DohDnsMessage.query(value, 1).let { it.copyOfRange(12, it.size - 4) }
    private fun rr(type: Int, data: ByteArray, ttl: Int = 60, owner: ByteArray = byteArrayOf(0xc0.toByte(), 12)) =
        owner + byteArrayOf(0, type.toByte(), 0, 1, 0, 0, (ttl shr 8).toByte(), ttl.toByte(), (data.size shr 8).toByte(), data.size.toByte()) + data
    private fun response(query: ByteArray, vararg records: ByteArray) = query.copyOf().also {
        it[2] = 0x81.toByte(); it[3] = 0x80.toByte(); it[7] = records.size.toByte()
    } + records.fold(byteArrayOf()) { bytes, record -> bytes + record }
    private fun addressResponse(query: ByteArray, ttl: Int = 60) = response(query, rr(1, byteArrayOf(1, 2, 3, 4), ttl))

    @Test
    fun encodesStandardWireQueryAndReadsCompressedIpv4AndIpv6() {
        val query = DohDnsMessage.query("www.example.com", 1)
        assertContentEquals(
            byteArrayOf(0,0,1,0,0,1,0,0,0,0,0,0,3,119,119,119,7,101,120,97,109,112,108,101,3,99,111,109,0,0,1,0,1), query,
        )
        val a = DohDnsMessage.answer(addressResponse(query), "www.example.com", 1)
        assertEquals(listOf("1.2.3.4"), a.addresses)
        assertEquals(60L, a.ttlSeconds)
        val aaaaQuery = DohDnsMessage.query(host, 28)
        val ip = byteArrayOf(0x20,1,0x0d,0xb8.toByte(),0,0,0,0,0,0,0,0,0,0,0,1)
        assertEquals(listOf("2001:db8:0:0:0:0:0:1"), DohDnsMessage.answer(response(aaaaQuery, rr(28, ip)), host, 28).addresses)
    }

    @Test
    fun followsCnameAndExcludesUnrelatedAnswers() {
        val query = DohDnsMessage.query(host, 1)
        val bytes = response(query,
            rr(5, name("alias.example"), ttl = 20),
            rr(1, byteArrayOf(9,9,9,9), owner = name("unrelated.example")),
            rr(1, byteArrayOf(1,2,3,4), owner = name("alias.example")),
        )
        assertEquals(listOf("1.2.3.4"), DohDnsMessage.answer(bytes, host, 1).addresses)
        assertEquals(20L, DohDnsMessage.answer(bytes, host, 1).ttlSeconds)
    }

    @Test
    fun rejectsMalformedTruncatedMismatchedAndLoopingResponses() {
        val query = DohDnsMessage.query(host, 1)
        val valid = addressResponse(query)
        assertFailsWith<IllegalArgumentException> { DohDnsMessage.answer(valid.copyOf(valid.size - 1), host, 1) }
        assertFailsWith<IllegalArgumentException> { DohDnsMessage.answer(valid, "different.example", 1) }
        assertFailsWith<IllegalArgumentException> { DohDnsMessage.answer(valid, host, 28) }
        for (flags in listOf(0x0100, 0x8380, 0x8183)) {
            val bad = valid.copyOf().also { it[2] = (flags shr 8).toByte(); it[3] = flags.toByte() }
            assertFailsWith<IllegalArgumentException> { DohDnsMessage.answer(bad, host, 1) }
        }
        val pointerLoop = valid.copyOf().also { it[12] = 0xc0.toByte(); it[13] = 12 }
        assertFailsWith<IllegalArgumentException> { DohDnsMessage.answer(pointerLoop, host, 1) }
        assertFailsWith<IllegalArgumentException> { DohDnsMessage.query("bad..example", 1) }
    }

    @Test
    fun retriesOnlyConfiguredEndpointsAndSupportsCnameOnlyResponses() = runBlocking {
        val visited = mutableListOf<String>()
        val resolver = resolver { _, endpoint, query ->
            visited += endpoint
            check(endpoint == endpoints[1])
            val bytes = if (query.contentEquals(DohDnsMessage.query(host, 1))) {
                response(query, rr(5, name("alias.example")))
            } else addressResponse(query)
            NodeDnsResolver.Response(bytes)
        }
        assertEquals(listOf("1.2.3.4"), resolver.resolve(host, endpoints, false, 1))
        assertEquals(listOf(endpoints[0], endpoints[1], endpoints[1]), visited)
    }

    @Test
    fun cachesUntilDnsTtlMinusHttpAgeAndSeparatesSessionsSettings() = runBlocking {
        var calls = 0
        val resolver = resolver { _, _, query -> calls++; NodeDnsResolver.Response(addressResponse(query), ageSeconds = 50) }
        resolver.resolve(host, endpoints, false, 1)
        time = 9_999
        resolver.resolve(host, endpoints, false, 1)
        assertEquals(1, calls)
        time = 10_000
        resolver.resolve(host, endpoints, false, 1)
        assertEquals(2, calls)
        resolver.resolve(host, endpoints, false, 2)
        assertEquals(3, calls)
        resolver.resolve(host, endpoints.take(1), false, 2)
        assertEquals(4, calls)
    }

    @Test
    fun literalIpsNeedNoDnsAndFailuresAreCachedWithoutSystemFallback() = runBlocking {
        var calls = 0
        val resolver = resolver { _, endpoint, _ -> assertTrue(endpoint in endpoints); calls++; error("DoH unavailable") }
        assertEquals(listOf("2001:db8::1"), resolver.resolve("2001:db8::1", emptyList(), false, 1))
        assertEquals(emptyList(), resolver.resolve(host, endpoints, false, 1))
        assertEquals(2, calls)
        resolver.resolve(host, endpoints, false, 1)
        assertEquals(2, calls)
        time = 30_000
        resolver.resolve(host, endpoints, false, 1)
        assertEquals(4, calls)
    }

    @Test
    fun ipv6QueriesFollowSettingAndResponsesAreCombined() = runBlocking {
        val types = mutableListOf<Int>()
        val resolver = resolver { _, _, query ->
            val type = query[query.size - 3].toInt()
            types += type
            NodeDnsResolver.Response(if (type == 1) addressResponse(query) else response(query, rr(28, ByteArray(16).also { it[15] = 1 })))
        }
        assertEquals(2, resolver.resolve(host, endpoints, true, 1).size)
        assertEquals(listOf(1,28), types)
    }

    @Test
    fun cancelledPageDoesNotDiscardInFlightDnsAnswer() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val ready = CompletableDeferred<Unit>()
        var calls = 0
        val resolver = resolver { _, _, query -> calls++; started.complete(Unit); ready.await(); NodeDnsResolver.Response(addressResponse(query)) }
        val page = async { resolver.resolve(host, endpoints, false, 1) }
        started.await()
        page.cancel()
        ready.complete(Unit)
        assertEquals(listOf("1.2.3.4"), resolver.resolve(host, endpoints, false, 1))
        assertEquals(1, calls)
    }
    @Test
    fun followsSelectedModeAndNeverDowngradesAnEnabledDohFailure() = runBlocking {
        val modes = mutableListOf<NodeDnsResolver.Mode>()
        val resolver = resolver { mode, _, query ->
            modes += mode
            if (mode == NodeDnsResolver.Mode.DOH) error("DoH failed")
            NodeDnsResolver.Response(addressResponse(query))
        }
        assertEquals(emptyList(), resolver.resolve(host, endpoints, false, 1, NodeDnsResolver.Mode.DOH))
        assertEquals(listOf(NodeDnsResolver.Mode.DOH, NodeDnsResolver.Mode.DOH), modes)
        assertEquals(listOf("1.2.3.4"), resolver.resolve(host, listOf("8.8.8.8"), false, 1, NodeDnsResolver.Mode.PLAIN))
        assertEquals(NodeDnsResolver.Mode.PLAIN, modes.last())
        val count = modes.size
        resolver.resolve(host, listOf("8.8.8.8"), false, 1, NodeDnsResolver.Mode.PLAIN)
        assertEquals(count, modes.size)
        resolver.resolve(host, listOf("1.1.1.1"), false, 1, NodeDnsResolver.Mode.PLAIN)
        assertEquals(count + 1, modes.size)
    }

}
