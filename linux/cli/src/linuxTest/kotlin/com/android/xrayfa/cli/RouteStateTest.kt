package com.android.xrayfa.cli

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RouteStateTest {
    private fun route(gateway: String, metric: Int = 100) = buildJsonObject {
        put("family", "inet")
        put("raw", "default via $gateway dev eth0 metric $metric")
        put("gateway", gateway)
        put("metric", metric)
    }

    @Test
    fun dumpOrderAndObjectKeyOrderDoNotChangeRoutes() {
        val first = route("192.0.2.1")
        val second = route("192.0.2.2", 200)
        val reorderedKeys = JsonObject(first.entries.reversed().associate { it.toPair() })
        val before = JsonArray(listOf(first, second))
        val after = JsonArray(listOf(second, reorderedKeys))
        assertEquals(normalizedRoutes(before), normalizedRoutes(after))
        assertNull(routeDifference(before, after))
        assertEquals(normalizedNetwork(buildJsonObject { put("routes", before) }),
            normalizedNetwork(buildJsonObject { put("routes", after) }))
    }

    @Test
    fun actualChangesAndDuplicateCountsAreStillRejectedAndExplained() {
        val first = route("192.0.2.1")
        val before = JsonArray(listOf(first))
        for (changed in listOf(route("192.0.2.2"), route("192.0.2.1", 101))) {
            val diff = assertNotNull(routeDifference(before, JsonArray(listOf(changed))))
            assertTrue("added=1" in diff && "removed=1" in diff)
        }
        val duplicate = assertNotNull(routeDifference(before, JsonArray(listOf(first, first))))
        assertTrue("added=1" in duplicate && "removed=0" in duplicate)
        val removed = assertNotNull(routeDifference(before, JsonArray(emptyList())))
        assertTrue("removed=1" in removed && "192.0.2.1" in removed)
    }

    @Test
    fun ruleOrderAndNestedNexthopOrderRemainSignificant() {
        val a = route("192.0.2.1")
        val b = route("192.0.2.2")
        val left = JsonArray(listOf(a, b))
        val right = JsonArray(listOf(b, a))
        assertNotEquals(normalizedNetwork(buildJsonObject { put("rules", left) }),
            normalizedNetwork(buildJsonObject { put("rules", right) }))
        assertNotNull(routeDifference(JsonArray(listOf(buildJsonObject { put("nexthops", left) })),
            JsonArray(listOf(buildJsonObject { put("nexthops", right) }))))
    }

    private fun dhcp(metric: Int = 600, gateway: String = "192.0.2.1", device: String = "eth0") = buildJsonObject {
        put("family", "inet")
        put("dst", "default")
        put("gateway", gateway)
        put("dev", device)
        put("protocol", "dhcp")
        put("table", "main")
        put("metric", metric)
        put("raw", "default via $gateway dev $device proto dhcp metric $metric")
    }

    @Test
    fun singleUnderlayDhcpConnectivityPenaltyCanToggleWithoutChangingPath() {
        val original = JsonArray(listOf(dhcp()))
        val penalty = JsonArray(listOf(dhcp(20600)))
        assertTrue(connectivityMetricChange(original, original, penalty, "eth0"))
        assertTrue(connectivityMetricChange(original, penalty, original, "eth0"))
        assertTrue(connectivityMetricChange(penalty, penalty, original, "eth0"))
        val withTun = JsonArray(listOf(dhcp(), route("198.18.0.1")))
        val changed = JsonArray(listOf(dhcp(20600), route("198.18.0.1")))
        assertTrue(connectivityMetricChange(original, withTun, changed, "eth0"))
    }

    @Test
    fun penaltyExceptionNeverAcceptsOtherRouteChangesOrUnboundedMetricDrift() {
        val original = JsonArray(listOf(dhcp()))
        val penalty = JsonArray(listOf(dhcp(20600)))
        for (after in listOf(
            JsonArray(listOf(dhcp(601))),
            JsonArray(listOf(dhcp(20600, gateway = "192.0.2.2"))),
            JsonArray(listOf(dhcp(20600, device = "eth1"))),
            JsonArray(listOf(dhcp(20600), dhcp(100, device = "eth1"))),
            JsonArray(listOf(dhcp(20600), route("203.0.113.1"))),
        )) assertFalse(connectivityMetricChange(original, original, after, "eth0"))
        assertFalse(connectivityMetricChange(original, penalty, JsonArray(listOf(dhcp(40600))), "eth0"))
        assertFalse(connectivityMetricChange(original, original, penalty, "eth1"))
        val static = JsonArray(listOf(JsonObject(dhcp() + ("protocol" to JsonPrimitive("static")))))
        assertFalse(connectivityMetricChange(static, static, penalty, "eth0"))
    }

    @Test
    fun metricDeleteAddGapIsNarrowAndOtherChangesRemainFatal() {
        val baseline = JsonArray(listOf(dhcp()))
        val tun = route("198.18.0.1")
        val expected = JsonArray(listOf(dhcp(), tun))
        assertTrue(connectivityMetricTransition(baseline, expected, JsonArray(listOf(tun)), "eth0"))
        assertTrue(connectivityMetricTransition(baseline, expected,
            JsonArray(listOf(dhcp(), dhcp(20600), tun)), "eth0"))
        assertFalse(connectivityMetricTransition(baseline, expected, JsonArray(emptyList()), "eth0"))
        assertFalse(connectivityMetricTransition(baseline, expected,
            JsonArray(listOf(dhcp(), dhcp(20600, gateway = "192.0.2.2"), tun)), "eth0"))
        assertFalse(connectivityMetricTransition(baseline, expected,
            JsonArray(listOf(tun, route("203.0.113.1"))), "eth0"))
        assertFalse(connectivityMetricTransition(baseline, expected,
            JsonArray(listOf(dhcp(), dhcp(601), tun)), "eth0"))
        assertFalse(connectivityMetricTransition(baseline, expected, JsonArray(listOf(tun)), "eth1"))
    }
}
