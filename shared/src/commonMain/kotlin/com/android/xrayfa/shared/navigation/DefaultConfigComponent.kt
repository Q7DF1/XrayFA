package com.android.xrayfa.shared.navigation

import com.android.xrayfa.repository.NodeRepository
import com.android.xrayfa.repository.SubscriptionRepository
import com.android.xrayfa.shared.config.ConfigLinkImporter
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
                val filters = buildFilters(subscriptions)
                val filteredNodes = filterNodes(allNodes, favorites, selectedFilterId)
                ConfigState(
                    nodes = filteredNodes,
                    subscriptions = subscriptions,
                    filters = filters,
                    selectedFilterId = selectedFilterId,
                )
            }.collect { configState ->
                _state.value = configState
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

    private fun refreshNodes() {
        scope.launch {
            val allNodes = nodeRepository.allNodes.first()
            val favorites = nodeRepository.favorites.first()
            _state.update {
                it.copy(
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
