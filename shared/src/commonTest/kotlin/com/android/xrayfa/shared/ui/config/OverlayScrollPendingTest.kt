package com.android.xrayfa.shared.ui.config

import com.android.xrayfa.model.Node
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OverlayScrollPendingTest {
    private val filtered = listOf(node(2), node(5))
    private val unfiltered = listOf(node(1), node(2), node(3), node(5))

    @Test
    fun doesNotCommitOnFirstBlankQueryFrameWhileNodesStillSearchSnapshot() {
        val pending =
            OverlayScrollPending(
                nodeId = 5,
                nodesAtTap = filtered,
                queryWasBlankAtTap = false,
            )
        assertFalse(
            shouldCommitOverlayScroll(
                pending = pending,
                searchQuery = "",
                currentNodes = filtered,
            ),
        )
    }

    @Test
    fun commitsWhenQueryBlankAndNodesIsNewList() {
        val pending =
            OverlayScrollPending(
                nodeId = 5,
                nodesAtTap = filtered,
                queryWasBlankAtTap = false,
            )
        assertTrue(
            shouldCommitOverlayScroll(
                pending = pending,
                searchQuery = "",
                currentNodes = unfiltered,
            ),
        )
    }

    @Test
    fun commitsImmediatelyWhenQueryWasAlreadyBlank() {
        val pending =
            OverlayScrollPending(
                nodeId = 5,
                nodesAtTap = unfiltered,
                queryWasBlankAtTap = true,
            )
        assertTrue(
            shouldCommitOverlayScroll(
                pending = pending,
                searchQuery = "",
                currentNodes = unfiltered,
            ),
        )
    }

    @Test
    fun waitsWhileSearchQueryStillNonBlank() {
        val pending =
            OverlayScrollPending(
                nodeId = 5,
                nodesAtTap = filtered,
                queryWasBlankAtTap = false,
            )
        assertFalse(
            shouldCommitOverlayScroll(
                pending = pending,
                searchQuery = "hk",
                currentNodes = filtered,
            ),
        )
    }

    @Test
    fun doesNotCommitWhenNothingPending() {
        assertFalse(
            shouldCommitOverlayScroll(
                pending = null,
                searchQuery = "",
                currentNodes = unfiltered,
            ),
        )
    }

    private fun node(id: Int): Node =
        Node(
            id = id,
            protocolPrefix = "vmess",
            address = "example.com",
            port = 443,
            subscriptionId = 0,
            url = "vmess://example.com/$id",
        )
}
