package com.android.xrayfa.shared.ui.config

import kotlin.test.Test
import kotlin.test.assertEquals

class SelectedNodeViewportTest {
    @Test
    fun unknownWhenNoSelection() {
        assertEquals(
            SelectedNodeViewport.Unknown,
            selectedNodeViewport(selectedIndex = -1, visibleItemIndices = listOf(0, 1, 2)),
        )
    }

    @Test
    fun unknownWhenListNotLaidOut() {
        assertEquals(
            SelectedNodeViewport.Unknown,
            selectedNodeViewport(selectedIndex = 3, visibleItemIndices = emptyList()),
        )
    }

    @Test
    fun visibleWhenSelectedIndexIsOnScreen() {
        assertEquals(
            SelectedNodeViewport.Visible,
            selectedNodeViewport(selectedIndex = 2, visibleItemIndices = listOf(1, 2, 3)),
        )
    }

    @Test
    fun aboveWhenSelectedIndexIsBeforeFirstVisible() {
        assertEquals(
            SelectedNodeViewport.Above,
            selectedNodeViewport(selectedIndex = 0, visibleItemIndices = listOf(4, 5, 6)),
        )
    }

    @Test
    fun belowWhenSelectedIndexIsAfterLastVisible() {
        assertEquals(
            SelectedNodeViewport.Below,
            selectedNodeViewport(selectedIndex = 10, visibleItemIndices = listOf(4, 5, 6)),
        )
    }
}
