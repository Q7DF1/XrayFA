package com.android.xrayfa.common.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DnsDefaultsTest {
    @Test
    fun acceptsHttpsDoHEndpointsAndIpv6LiteralHosts() {
        assertTrue(DnsDefaults.isValidDohServer("https://resolver.example/dns-query"))
        assertTrue(DnsDefaults.isValidDohServer("https://resolver.example:8443/query"))
        assertTrue(DnsDefaults.isValidDohServer("https://[2001:4860:4860::8888]/dns-query"))
        assertTrue(DnsDefaults.isValidDohServer("https://1.1.1.1/dns-query"))
    }

    @Test
    fun rejectsNonHttpsOrMalformedEndpoints() {
        assertFalse(DnsDefaults.isValidDohServer("udp://1.1.1.1"))
        assertFalse(DnsDefaults.isValidDohServer("http://resolver.example/dns-query"))
        assertFalse(DnsDefaults.isValidDohServer("https:///dns-query"))
        assertFalse(DnsDefaults.isValidDohServer("https://resolver.example:"))
        assertFalse(DnsDefaults.isValidDohServer("https://user@resolver.example/dns-query"))
    }

    @Test
    fun invalidOrEmptyPersistedValuesFallBackToHttpsDefaults() {
        assertEquals(DnsDefaults.dohServers, DnsDefaults.parseDohServers(""))
        assertEquals(DnsDefaults.dohServers, DnsDefaults.parseDohServers("udp://1.1.1.1"))
        assertEquals(listOf("https://resolver.example/dns-query"), DnsDefaults.parseDohServers(" https://resolver.example/dns-query "))
    }
}
