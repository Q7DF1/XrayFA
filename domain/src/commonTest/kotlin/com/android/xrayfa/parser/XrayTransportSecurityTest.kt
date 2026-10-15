package com.android.xrayfa.parser

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class XrayTransportSecurityTest {
    @Test
    fun matchesPinnedCorePrivateRangesAndDomainBoundaries() {
        for (host in listOf("0.1.2.3", "10.0.0.1", "100.64.1.1", "127.0.0.1", "169.254.0.1", "172.31.0.1",
            "192.0.0.1", "192.0.2.1", "192.88.99.1", "192.168.1.1", "198.19.0.1", "198.51.100.1",
            "203.0.113.1", "224.1.1.1", "255.255.255.255", "::", "::1", "fc00::1", "fdff::1", "fe80::1", "ff02::1",
            "[::1]", "::ffff:192.168.1.1", "localhost", "NAS", "router.lan", "host.home.arpa", "SERVICE.LOCAL.", "example")) {
            assertTrue(isXrayPrivateEndpoint(host), host)
        }
        for (host in listOf("1.1.1.1", "8.8.8.8", "100.128.0.1", "172.32.0.1", "192.169.1.1", "198.20.0.1",
            "2001:db8::1", "2001:4860::1", "fe00::1", "::2", "example.com", "notlocalhost.com", "localhost.com", "host.home.arpa.com")) {
            assertFalse(isXrayPrivateEndpoint(host), host)
        }
    }
}
