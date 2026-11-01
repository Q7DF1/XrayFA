package com.android.xrayfa.shared.navigation

import com.android.xrayfa.model.Node
import kotlin.test.Test
import kotlin.test.assertEquals

class ConfigNodeSortingTest {
    private fun node(id: Int) = Node(
        id = id, protocolPrefix = "vless", address = "example.invalid", port = 443,
        subscriptionId = 0, url = "vless://example.invalid/$id",
    )

    @Test
    fun successfulDelaysComeFirstAndEqualOrInvalidResultsKeepTheirOrder() {
        val nodes = (1..7).map(::node)
        val delays = mapOf(1 to -2L, 3 to 90L, 4 to 20L, 5 to 90L, 6 to -1L, 7 to 0L)
        assertEquals(listOf(4, 3, 5, 1, 2, 6, 7), sortNodesByDelay(nodes, delays).map { it.id })
        assertEquals((1..7).toList(), nodes.map { it.id })
    }

    @Test
    fun noMeasurementsPreserveDefaultOrder() {
        val nodes = listOf(node(3), node(1), node(2))
        assertEquals(nodes, sortNodesByDelay(nodes, emptyMap()))
    }
}
