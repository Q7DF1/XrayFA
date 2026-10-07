package com.android.xrayfa.shared.navigation

import com.android.xrayfa.common.core.DelayMeasurement
import com.android.xrayfa.common.core.DelayProbe
import com.android.xrayfa.common.core.XrayCore
import com.android.xrayfa.common.core.configDelayTestAllEnabled
import com.android.xrayfa.datastore.SettingsRepository
import com.android.xrayfa.model.Node
import com.android.xrayfa.model.isJsonConfig
import com.android.xrayfa.config.JsonConfigException
import com.android.xrayfa.shared.config.JsonConfigEditor
import com.android.xrayfa.shared.vpn.prepareJsonVpn
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
    private val jsonEditor: JsonConfigEditor = JsonConfigEditor(nodeRepository),
) : ConfigComponent,
    ComponentContext by componentContext {
    private val scope = coroutineScope()
    private val delayProbe: DelayProbe = createDelayProbe(xrayCore, parserFactory)
    private val configMutex = kotlinx.coroutines.sync.Mutex()

    private val _state = MutableValue(ConfigState())
    override val state: Value<ConfigState> = _state

    private var allNodesCache: List<Node> = emptyList()
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
                allNodesCache = allNodes
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

    override fun nodeById(id: Int): Node? =
        if (id <= 0) null else allNodesCache.firstOrNull { it.id == id }

    override fun onSelectFilter(filterId: Int) {
        selectedFilterId = filterId
        refreshNodes()
    }

    override fun onSelectNode(nodeId: Int, onSelected: () -> Unit) {
        scope.launch {
            configMutex.lock()
            try {
                val candidate = nodeRepository.loadLinksById(nodeId).first() ?: return@launch
                if (candidate.isJsonConfig) withContext(Dispatchers.Default) {
                    prepareJsonVpn(candidate, settingsRepository.settingsFlow.first())
                }
                if (nodeId != nodeRepository.querySelectedNode().first()?.id) {
                    nodeRepository.clearSelection()
                    nodeRepository.updateSelectById(nodeId, selected = true)
                    vpnController.restartIfNeeded()
                }
                onSelected()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.update { it.copy(configError = (e as? JsonConfigException)?.reason?.name ?: "INVALID_CONFIG") }
            } finally { configMutex.unlock() }
        }
    }

    override fun onDismissConfigError() { _state.update { it.copy(configError = null) } }

    override fun onSaveJsonConfig(id: Int, name: String, text: String, inboundTag: String?, onDone: (String?) -> Unit) {
        scope.launch {
            configMutex.lock()
            try {
                withContext(Dispatchers.Default) {
                    com.android.xrayfa.config.JsonVpnConfig.prepare(text, com.android.xrayfa.shared.vpn.jsonVpnTransport(settingsRepository.settingsFlow.first()), inboundTag)
                    jsonEditor.save(id, name, text, inboundTag)
                }
                if (id > 0 && nodeRepository.querySelectedNode().first()?.id == id) vpnController.restartIfNeeded()
                onDone(null)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                onDone((e as? JsonConfigException)?.reason?.name ?: "INVALID_CONFIG")
            } finally { configMutex.unlock() }
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

    override fun onSaveNodeEdit(
        nodeId: Int,
        form: NodeEditForm,
        onDone: (Boolean) -> Unit,
    ) {
        scope.launch {
            val success = nodeFormEditor.saveForm(nodeId, form)
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
            configMutex.lock()
            try { nodeEditor.deleteNode(nodeId) } finally { configMutex.unlock() }
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
            configMutex.lock()
            try {
                val selectedJson = nodeRepository.querySelectedNode().first()?.isJsonConfig == true
                nodeRepository.deleteAllNodes()
                if (selectedJson) {
                    vpnController.clearPendingConfig()
                    vpnController.disconnect()
                }
            } finally { configMutex.unlock() }
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
                val nodes = _state.value.nodes.filterNot { it.isJsonConfig }
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
            allNodesCache = allNodes
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
