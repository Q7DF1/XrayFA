package com.android.xrayfa.shared.navigation

import com.android.xrayfa.common.core.DelayMeasurement
import com.android.xrayfa.common.core.DelayProbe
import com.android.xrayfa.common.core.XrayCore
import com.android.xrayfa.common.core.configDelayTestAllEnabled
import com.android.xrayfa.datastore.SettingsRepository
import com.android.xrayfa.model.Node
import com.android.xrayfa.parser.ParserFactory
import com.android.xrayfa.repository.NodeRepository
import com.android.xrayfa.repository.SubscriptionRepository
import com.android.xrayfa.shared.config.ConfigLinkImporter
import com.android.xrayfa.shared.config.NodeEditForm
import com.android.xrayfa.shared.config.NodeEditor
import com.android.xrayfa.shared.config.NodeFormEditor
import com.android.xrayfa.shared.vpn.createDelayProbe
import com.android.xrayfa.vpn.VpnController
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.update
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope as nestedCoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

class DefaultConfigComponent(
    componentContext: ComponentContext,
    private val nodeRepository: NodeRepository,
    subscriptionRepository: SubscriptionRepository,
    private val vpnController: VpnController,
    private val configLinkImporter: ConfigLinkImporter,
    private val nodeEditor: NodeEditor,
    private val nodeFormEditor: NodeFormEditor,
    private val filterLabels: ConfigFilterLabels = ConfigFilterLabels(),
    private val settingsRepository: SettingsRepository,
    xrayCore: XrayCore,
    parserFactory: ParserFactory,
) : ConfigComponent,
    ComponentContext by componentContext {
    private val scope = coroutineScope()
    private val delayProbe: DelayProbe = createDelayProbe(xrayCore, parserFactory)

    private val _state = MutableValue(ConfigState())
    override val state: Value<ConfigState> = _state

    private var selectedFilterId: Int = ConfigFilterIds.SUB_ALL
    private var searchQuery: String = ""

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
                val filteredNodes = filterNodes(allNodes, favorites, selectedFilterId, searchQuery)
                _state.update { current ->
                    current.copy(
                        nodes = filteredNodes,
                        subscriptions = subscriptions,
                        filters = filters,
                        selectedFilterId = selectedFilterId,
                        searchQuery = searchQuery,
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

    override fun onOpenEditNode(nodeId: Int) = Unit

    override fun onOpenCreateNode() = Unit

    override fun onCloseNodeEdit() {
        _state.update { it.copy(editError = false) }
    }

    override fun onSaveNodeEdit(
        nodeId: Int,
        form: NodeEditForm,
        onDone: (Boolean) -> Unit,
    ) {
        scope.launch {
            val success = nodeFormEditor.saveForm(nodeId, form)
            _state.update { it.copy(editError = !success) }
            onDone(success)
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

    override fun onShowDeleteAll() {
        _state.update { it.copy(pendingDeleteAll = true) }
    }

    override fun onDismissDeleteAll() {
        _state.update { it.copy(pendingDeleteAll = false) }
    }

    override fun onConfirmDeleteAll() {
        scope.launch {
            nodeRepository.deleteAllNodes()
            _state.update { it.copy(pendingDeleteAll = false) }
        }
    }

    override fun onSearch(query: String) {
        searchQuery = query
        _state.update { it.copy(searchQuery = query) }
        refreshNodes()
    }

    override fun onTestAllDelays() {
        if (!configDelayTestAllEnabled(_state.value.testingAll)) return
        scope.launch {
            _state.update { it.copy(testingAll = true) }
            try {
                val testUrl = settingsRepository.settingsFlow.first().delayTestUrl
                val nodes = _state.value.nodes
                nestedCoroutineScope {
                    val semaphore = Semaphore(CONFIG_DELAY_CONCURRENCY)
                    nodes.forEach { node ->
                        launch {
                            semaphore.withPermit {
                                _state.update {
                                    it.copy(
                                        nodeDelayMap =
                                            it.nodeDelayMap + (node.id to DelayMeasurement.TESTING_SENTINEL),
                                    )
                                }
                                val delay =
                                    withContext(Dispatchers.Default) {
                                        delayProbe.measureNode(node.url, testUrl)
                                    }
                                _state.update {
                                    it.copy(nodeDelayMap = it.nodeDelayMap + (node.id to delay))
                                }
                            }
                        }
                    }
                }
            } finally {
                _state.update { it.copy(testingAll = false) }
            }
        }
    }

    private fun refreshNodes() {
        scope.launch {
            val allNodes = nodeRepository.allNodes.first()
            val favorites = nodeRepository.favorites.first()
            _state.update { current ->
                current.copy(
                    nodes = filterNodes(allNodes, favorites, selectedFilterId, searchQuery),
                    selectedFilterId = selectedFilterId,
                    searchQuery = searchQuery,
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
        query: String,
    ): List<com.android.xrayfa.model.Node> {
        val filtered =
            when (filterId) {
                ConfigFilterIds.SUB_ALL -> allNodes
                ConfigFilterIds.SUB_FAVORITE -> favorites
                else -> allNodes.filter { it.subscriptionId == filterId }
            }
        val reversed = filtered.reversed()
        if (query.isBlank()) {
            return reversed
        }
        return reversed.filter { node ->
            node.remark?.contains(query, ignoreCase = true) == true ||
                node.url.contains(query, ignoreCase = true)
        }
    }

    private companion object {
        const val CONFIG_DELAY_CONCURRENCY = 32
    }
}

/** Localized filter chip labels (Android passes stringResource values). */
data class ConfigFilterLabels(
    val manualLabel: String = "Manual",
    val allLabel: String = "All",
    val favoriteLabel: String = "Favorite",
)
