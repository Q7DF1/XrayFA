package com.android.xrayfa.shared.navigation

import com.android.xrayfa.model.Node
import com.android.xrayfa.shared.config.NodeEditForm
import com.arkivanov.decompose.value.Value

interface ConfigComponent {
    val state: Value<ConfigState>

    /** Unfiltered lookup (repository / cache), never [ConfigState.nodes]. */
    fun nodeById(id: Int): Node?

    fun onSelectFilter(filterId: Int)

    fun onSelectNode(nodeId: Int)

    fun onToggleFavorite(
        nodeId: Int,
        favorite: Boolean,
    )

    fun onImportFromClipboard()

    fun onImportFromLink(link: String)

    fun onSaveNodeEdit(
        nodeId: Int,
        form: NodeEditForm,
        onDone: (Boolean) -> Unit = {},
    )

    fun onShowDeleteNode(node: Node)

    fun onDismissDeleteNode()

    fun onConfirmDeleteNode()

    fun onShowDeleteAll()

    fun onDismissDeleteAll()

    fun onConfirmDeleteAll()

    fun onSearch(query: String)

    fun onTestAllDelays()
}

/** Typealias for E.6e naming; prefer [ConfigComponent] in new code. */
typealias ConfigTabComponent = ConfigComponent
