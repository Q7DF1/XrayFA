package com.android.xrayfa.common.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DelayProbeTest {

    @Test
    fun mapNativeDelayResult_nonPositiveBecomesTimeout() {
        assertEquals(DelayMeasurement.TIMEOUT_SENTINEL, mapNativeDelayResult(0L))
        assertEquals(DelayMeasurement.TIMEOUT_SENTINEL, mapNativeDelayResult(-1L))
        assertEquals(128L, mapNativeDelayResult(128L))
    }

    @Test
    fun homeTest_requiresVpnAndIdle() {
        assertTrue(homeDelayTestEnabled(vpnConnected = true, testing = false))
        assertFalse(homeDelayTestEnabled(vpnConnected = false, testing = false))
        assertFalse(homeDelayTestEnabled(vpnConnected = true, testing = true))
    }

    @Test
    fun configTestAll_requiresIdle() {
        assertTrue(configDelayTestAllEnabled(testingAll = false))
        assertFalse(configDelayTestAllEnabled(testingAll = true))
    }

    @Test
    fun home_usesLiveDelayWhenPositive() = runTestBlocking {
        var outboundCalls = 0
        val probe =
            DelayProbe(
                measureLive = { 42L },
                parseConfig = { error("parse should not run") },
                measureOutbound = { _, _ ->
                    outboundCalls++
                    99L
                },
            )

        assertEquals(42L, probe.measureHome(testUrl = "https://test", selectedNodeUrl = "vless://x"))
        assertEquals(0, outboundCalls)
    }

    @Test
    fun home_fallsBackToOutboundWhenLiveFails() = runTestBlocking {
        var parsedUrl: String? = null
        val probe =
            DelayProbe(
                measureLive = { -1L },
                parseConfig = { url ->
                    parsedUrl = url
                    "config-json"
                },
                measureOutbound = { config, url ->
                    assertEquals("config-json", config)
                    assertEquals("https://test", url)
                    77L
                },
            )

        assertEquals(77L, probe.measureHome(testUrl = "https://test", selectedNodeUrl = "vless://node"))
        assertEquals("vless://node", parsedUrl)
    }

    @Test
    fun home_timeoutWhenLiveFailsAndNoNode() = runTestBlocking {
        val probe =
            DelayProbe(
                measureLive = { -1L },
                parseConfig = { error("parse should not run") },
                measureOutbound = { _, _ -> error("outbound should not run") },
            )

        assertEquals(
            DelayMeasurement.TIMEOUT_SENTINEL,
            probe.measureHome(testUrl = "https://test", selectedNodeUrl = null),
        )
    }

    @Test
    fun node_parseFailureIsTimeout() = runTestBlocking {
        val probe =
            DelayProbe(
                measureLive = { error("unused") },
                parseConfig = { error("bad url") },
                measureOutbound = { _, _ -> 1L },
            )

        assertEquals(DelayMeasurement.TIMEOUT_SENTINEL, probe.measureNode("bad://x", "https://test"))
    }
}

private fun runTestBlocking(block: suspend () -> Unit) {
    kotlinx.coroutines.runBlocking { block() }
}
