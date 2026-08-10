package com.android.xrayfa.shared.navigation

import com.android.xrayfa.model.Node
import com.android.xrayfa.repository.NodeRepository
import com.android.xrayfa.repository.SubscriptionRepository
import com.android.xrayfa.shared.config.ConfigLinkImporter
import com.android.xrayfa.shared.config.NodeEditForm
import com.android.xrayfa.shared.config.NodeEditor
import com.android.xrayfa.shared.config.NodeFormEditor
import com.android.xrayfa.vpn.VpnController
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.update
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class DefaultConfigComponent(
    componentContext: ComponentContext,
    private val nodeRepository: NodeRepository,
    subscriptionRepository: SubscriptionRepository,
    private val vpnController: VpnController,
    private val configLinkImporter: ConfigLinkImporter,
    private val nodeEditor: NodeEditor,
    private val nodeFormEditor: NodeFormEditor,
    private val filterLabels: ConfigFilterLabels = ConfigFilterLabels(),
) : ConfigComponent,
    ComponentContext by componentContext {
    private val scope = coroutineScope()

    private val _state = MutableValue(ConfigState())
    override val state: Value<ConfigState> = _state

    private var selectedFilterId: Int = ConfigFilterIds.SUB_ALL

    init {
        scope.launch {
            combine(
                nodeRepository.allNodes,
                nodeRepository.favorites,
                subscriptionRepository.allSubscriptions,
            ) { allNodes, favorites, subscriptions ->
                Triple(allNodes, favorites, subscriptions)
            }.collect { (allNodes, favorites, subscriptions) ->
                val filters = buildFilters(subscriptions)
                val filteredNodes = filterNodes(allNodes, favorites, selectedFilterId)
                _state.update { current ->
                    current.copy(
                        nodes = filteredNodes,
                        subscriptions = subscriptions,
                        filters = filters,
                        selectedFilterId = selectedFilterId,
                    )
                }
            }
        }
    }

    override fun onSelectFilter(filterId: Int) {
        selectedFilterId = filterId
        refreshNodes()
    }

    override fun onSelectNode(nodeId: Int) {
        scope.launch {
            if (nodeId == nodeRepository.querySelectedNode().first()?.id) {
                return@launch
            }
            nodeRepository.clearSelection()
            nodeRepository.updateSelectById(nodeId, selected = true)
            vpnController.restartIfNeeded()
        }
    }

    override fun onToggleFavorite(
        nodeId: Int,
        favorite: Boolean,
    ) {
        scope.launch {
            nodeRepository.updateFavoriteById(nodeId, favorite)
        }
    }

    override fun onImportFromClipboard() {
        scope.launch {
            configLinkImporter.importFromClipboard()
        }
    }

    override fun onImportFromLink(link: String) {
        scope.launch {
            configLinkImporter.addLink(link)
        }
    }

    override fun onOpenEditNode(nodeId: Int) {
        scope.launch {
            val node = nodeRepository.loadLinksById(nodeId).first() ?: return@launch
            _state.update {
                it.copy(nodeEditTarget = NodeEditTarget.Edit(node), editError = false)
            }
        }
    }

    override fun onOpenCreateNode() {
        _state.update {
            it.copy(nodeEditTarget = NodeEditTarget.Create, editError = false)
        }
    }

    override fun onCloseNodeEdit() {
        _state.update {
            it.copy(nodeEditTarget = null, editError = false)
        }
    }

    override fun onSaveNodeEdit(form: NodeEditForm) {
        val target = _state.value.nodeEditTarget ?: return
        val nodeId =
            when (target) {
                is NodeEditTarget.Create -> 0
                is NodeEditTarget.Edit -> target.node.id
            }
        scope.launch {
            val success = nodeFormEditor.saveForm(nodeId, form)
            if (success) {
                _state.update {
                    it.copy(nodeEditTarget = null, editError = false)
                }
            } else {
                _state.update {
                    it.copy(editError = true)
                }
            }
        }
    }

    override fun onShowDeleteNode(node: Node) {
        _state.update {
            it.copy(deleteTarget = node)
        }
    }

    override fun onDismissDeleteNode() {
        _state.update {
            it.copy(deleteTarget = null)
        }
    }

    override fun onConfirmDeleteNode() {
        val nodeId = _state.value.deleteTarget?.id ?: return
        scope.launch {
            nodeEditor.deleteNode(nodeId)
            _state.update {
                it.copy(deleteTarget = null)
            }
        }
    }

    private fun refreshNodes() {
        scope.launch {
            val allNodes = nodeRepository.allNodes.first()
            val favorites = nodeRepository.favorites.first()
            _state.update { current ->
                current.copy(
                    nodes = filterNodes(allNodes, favorites, selectedFilterId),
                    selectedFilterId = selectedFilterId,
                )
            }
        }
    }

    private fun buildFilters(subscriptions: List<com.android.xrayfa.model.Subscription>): List<ConfigFilterOption> {
        val filters = mutableListOf<ConfigFilterOption>()
        if (subscriptions.isNotEmpty()) {
            filters.add(ConfigFilterOption(ConfigFilterIds.SUB_MANUAL, filterLabels.manualLabel))
        }
        filters.add(ConfigFilterOption(ConfigFilterIds.SUB_ALL, filterLabels.allLabel))
        filters.add(ConfigFilterOption(ConfigFilterIds.SUB_FAVORITE, filterLabels.favoriteLabel))
        subscriptions.forEach { subscription ->
            filters.add(ConfigFilterOption(subscription.id, subscription.mark.orEmpty()))
        }
        return filters
    }

    private fun filterNodes(
        allNodes: List<com.android.xrayfa.model.Node>,
        favorites: List<com.android.xrayfa.model.Node>,
        filterId: Int,
    ): List<com.android.xrayfa.model.Node> {
        val filtered =
            when (filterId) {
                ConfigFilterIds.SUB_ALL -> allNodes
                ConfigFilterIds.SUB_FAVORITE -> favorites
                else -> allNodes.filter { it.subscriptionId == filterId }
            }
        return filtered.reversed()
    }
}

/** Localized filter chip labels (Android passes stringResource values). */
data class ConfigFilterLabels(
    val manualLabel: String = "Manual",
    val allLabel: String = "All",
    val favoriteLabel: String = "Favorite",
)
