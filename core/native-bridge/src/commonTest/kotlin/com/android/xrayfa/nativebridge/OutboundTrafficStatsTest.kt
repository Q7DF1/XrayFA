package com.android.xrayfa.nativebridge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OutboundTrafficStatsTest {

    @Test
    fun empty_returnsNoCounters() {
        assertTrue(parseOutboundTrafficStats("").isEmpty())
        assertEquals(0L, outboundTrafficValue("", tag = "proxy", direction = "uplink"))
    }

    @Test
    fun snapshot_parsesProxyUplinkAndDownlink() {
        val raw = "proxy,uplink,1200;proxy,downlink,3400;"

        assertEquals(1200L, outboundTrafficValue(raw, tag = "proxy", direction = "uplink"))
        assertEquals(3400L, outboundTrafficValue(raw, tag = "proxy", direction = "downlink"))
        assertEquals(0L, outboundTrafficValue(raw, tag = "direct", direction = "uplink"))
    }

    @Test
    fun malformedEntries_areSkipped() {
        val raw = "proxy,uplink,10;broken;proxy,downlink,not-a-number;direct,uplink,5;"

        assertEquals(10L, outboundTrafficValue(raw, tag = "proxy", direction = "uplink"))
        assertEquals(0L, outboundTrafficValue(raw, tag = "proxy", direction = "downlink"))
        assertEquals(5L, outboundTrafficValue(raw, tag = "direct", direction = "uplink"))
    }
}
