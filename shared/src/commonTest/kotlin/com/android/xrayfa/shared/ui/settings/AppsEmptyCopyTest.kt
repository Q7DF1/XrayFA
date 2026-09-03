package com.android.xrayfa.shared.ui.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppsEmptyCopyTest {
    @Test
    fun blankQueryAndEmptyListUsesNoPackagesMessage() {
        assertEquals(
            "No per-app allow list configured yet.",
            appsListEmptyMessage(
                itemsEmpty = true,
                searchQuery = "",
                noPackagesMessage = "No per-app allow list configured yet.",
                noMatchesMessage = "No matching apps",
            ),
        )
    }

    @Test
    fun whitespaceQueryAndEmptyListUsesNoPackagesMessage() {
        assertEquals(
            "No per-app allow list configured yet.",
            appsListEmptyMessage(
                itemsEmpty = true,
                searchQuery = "   ",
                noPackagesMessage = "No per-app allow list configured yet.",
                noMatchesMessage = "No matching apps",
            ),
        )
    }

    @Test
    fun nonBlankQueryAndEmptyListUsesNoMatchesMessage() {
        assertEquals(
            "No matching apps",
            appsListEmptyMessage(
                itemsEmpty = true,
                searchQuery = "chrome",
                noPackagesMessage = "No per-app allow list configured yet.",
                noMatchesMessage = "No matching apps",
            ),
        )
    }

    @Test
    fun nonEmptyListHasNoEmptyMessage() {
        assertNull(
            appsListEmptyMessage(
                itemsEmpty = false,
                searchQuery = "chrome",
                noPackagesMessage = "No per-app allow list configured yet.",
                noMatchesMessage = "No matching apps",
            ),
        )
    }
}
