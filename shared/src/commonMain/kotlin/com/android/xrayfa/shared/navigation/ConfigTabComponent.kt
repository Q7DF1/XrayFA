package com.android.xrayfa.shared.navigation

import com.arkivanov.decompose.value.Value

interface ConfigComponent {
    val state: Value<ConfigState>

    fun onSelectFilter(filterId: Int)

    fun onSelectNode(nodeId: Int)

    fun onToggleFavorite(
        nodeId: Int,
        favorite: Boolean,
    )

    fun onImportFromClipboard()
}

/** Typealias for E.6e naming; prefer [ConfigComponent] in new code. */
typealias ConfigTabComponent = ConfigComponent
