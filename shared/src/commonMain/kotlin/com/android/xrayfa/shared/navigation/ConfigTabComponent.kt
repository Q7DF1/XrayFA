package com.android.xrayfa.shared.navigation

import com.android.xrayfa.model.Node
import com.android.xrayfa.shared.config.NodeEditForm
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

    fun onImportFromLink(link: String)

    fun onOpenEditNode(nodeId: Int)

    fun onOpenCreateNode()

    fun onCloseNodeEdit()

    fun onSaveNodeEdit(form: NodeEditForm)

    fun onShowDeleteNode(node: Node)

    fun onDismissDeleteNode()

    fun onConfirmDeleteNode()

    fun onTestAllDelays()
}

/** Typealias for E.6e naming; prefer [ConfigComponent] in new code. */
typealias ConfigTabComponent = ConfigComponent
