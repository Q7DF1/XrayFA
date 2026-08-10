package com.android.xrayfa.shared.navigation

import com.arkivanov.decompose.value.Value
import com.android.xrayfa.model.Node

interface ConfigComponent {
    val state: Value<ConfigState>

    fun onSelectFilter(filterId: Int)

    fun onSelectNode(nodeId: Int)

    fun onToggleFavorite(
        nodeId: Int,
        favorite: Boolean,
    )

    fun onImportFromClipboard()

    fun onImportFromLink(link: String)

    fun onOpenEditNode(nodeId: Int)

    fun onCloseEditNode()

    fun onSaveEditNode(
        remark: String,
        link: String,
    )

    fun onShowDeleteNode(node: Node)

    fun onDismissDeleteNode()

    fun onConfirmDeleteNode()
}

/** Typealias for E.6e naming; prefer [ConfigComponent] in new code. */
typealias ConfigTabComponent = ConfigComponent
