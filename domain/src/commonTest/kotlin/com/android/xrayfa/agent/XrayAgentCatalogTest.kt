package com.android.xrayfa.agent

import com.android.xrayfa.model.Node
import com.android.xrayfa.model.Subscription
import com.android.xrayfa.repository.NodeRepository
import com.android.xrayfa.repository.SubscriptionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class XrayAgentCatalogTest {

    @Test
    fun listNodes_all_mapsSummariesWithoutShareLinkOrJson() = runBlocking {
        val catalog = catalog(
            nodes = listOf(
                testNode(id = 1, url = "vless://secret-uuid@host:443", jsonData = """{"id":"secret-uuid"}"""),
            ),
        )

        val summaries = catalog.listNodes(AgentNodeFilter.All)

        assertEquals(1, summaries.size)
        val summary = summaries.single()
        assertEquals(1, summary.id)
        assertEquals("manual-1", summary.remark)
        assertEquals("vless", summary.protocol)
        assertEquals("example.com", summary.address)
        assertEquals(443, summary.port)
        assertFalse(summary.selected)
        assertFalse(summary.favorite)
        assertEquals(AgentNodeFilter.MANUAL_SUBSCRIPTION_ID, summary.subscriptionId)
        assertEquals("US", summary.countryIso)
        assertFalse(summary.toString().contains("secret-uuid"))
        assertFalse(summary.toString().contains("vless://"))
    }

    @Test
    fun listNodes_favorites_returnsOnlyFavorited() = runBlocking {
        val catalog = catalog(
            nodes = listOf(
                testNode(id = 1, favorite = false),
                testNode(id = 2, remark = "starred", favorite = true),
            ),
        )

        val summaries = catalog.listNodes(AgentNodeFilter.Favorites)

        assertEquals(listOf(2), summaries.map { it.id })
        assertTrue(summaries.single().favorite)
    }

    @Test
    fun listNodes_manual_returnsUnsubscribedNodes() = runBlocking {
        val catalog = catalog(
            nodes = listOf(
                testNode(id = 1, subscriptionId = AgentNodeFilter.MANUAL_SUBSCRIPTION_ID),
                testNode(id = 2, subscriptionId = 7, remark = "from-sub"),
            ),
        )

        assertEquals(listOf(1), catalog.listNodes(AgentNodeFilter.Manual).map { it.id })
    }

    @Test
    fun listNodes_subscriptionId_filtersById() = runBlocking {
        val catalog = catalog(
            nodes = listOf(
                testNode(id = 1, subscriptionId = 7),
                testNode(id = 2, subscriptionId = 8, remark = "other"),
            ),
        )

        val summaries = catalog.listNodes(AgentNodeFilter.subscription(7))

        assertEquals(listOf(1), summaries.map { it.id })
    }

    @Test
    fun getSelectedNode_returnsNullWhenNoneSelected() = runBlocking {
        val catalog = catalog(nodes = listOf(testNode(id = 1, selected = false)))

        assertNull(catalog.getSelectedNode())
    }

    @Test
    fun getSelectedNode_returnsSummaryOfSelected() = runBlocking {
        val catalog = catalog(
            nodes = listOf(
                testNode(id = 1, selected = false),
                testNode(id = 2, remark = "current", selected = true),
            ),
        )

        assertEquals(2, catalog.getSelectedNode()?.id)
        assertEquals("current", catalog.getSelectedNode()?.remark)
        assertTrue(catalog.getSelectedNode()!!.selected)
    }

    @Test
    fun getNode_returnsNullWhenMissing() = runBlocking {
        val catalog = catalog(nodes = listOf(testNode(id = 1)))

        assertNull(catalog.getNode(99))
    }

    @Test
    fun listSubscriptions_omitsUrl() = runBlocking {
        val catalog = catalog(
            subscriptions = listOf(
                Subscription(
                    id = 3,
                    mark = "work",
                    url = "https://example.com/sub?token=secret-token",
                    isAutoUpdate = true,
                ),
            ),
        )

        val summaries = catalog.listSubscriptions()

        assertEquals(1, summaries.size)
        assertEquals(3, summaries.single().id)
        assertEquals("work", summaries.single().mark)
        assertTrue(summaries.single().autoUpdate)
        assertFalse(summaries.single().toString().contains("secret-token"))
        assertFalse(summaries.single().toString().contains("https://"))
    }

    @Test
    fun selectNode_marksRequestedAndClearsOthers() = runBlocking {
        val nodes = FakeNodeRepository(
            listOf(
                testNode(id = 1, selected = true),
                testNode(id = 2, selected = false),
            ),
        )
        val catalog = XrayAgentCatalog(nodes, FakeSubscriptionRepository())

        val result = catalog.selectNode(2)

        assertIs<AgentActionResult.Success>(result)
        assertEquals(2, catalog.getSelectedNode()?.id)
        assertEquals(1, nodes.current().count { it.selected })
    }

    @Test
    fun selectNode_missingId_returnsNodeNotFound() = runBlocking {
        val catalog = catalog(nodes = listOf(testNode(id = 1)))

        val result = catalog.selectNode(99)

        val failure = assertIs<AgentActionResult.Failure>(result)
        assertEquals(AgentErrorCode.NODE_NOT_FOUND, failure.code)
    }

    @Test
    fun setFavorite_updatesFlag() = runBlocking {
        val catalog = catalog(nodes = listOf(testNode(id = 1, favorite = false)))

        val result = catalog.setFavorite(1, true)

        assertIs<AgentActionResult.Success>(result)
        assertTrue(catalog.getNode(1)!!.favorite)
    }

    @Test
    fun setFavorite_missingId_returnsNodeNotFound() = runBlocking {
        val catalog = catalog(nodes = listOf(testNode(id = 1)))

        val result = catalog.setFavorite(99, true)

        val failure = assertIs<AgentActionResult.Failure>(result)
        assertEquals(AgentErrorCode.NODE_NOT_FOUND, failure.code)
    }

    @Test
    fun refreshSubscription_missingId_returnsNotFound() = runBlocking {
        val result = catalog().refreshSubscription(9)

        val failure = assertIs<AgentActionResult.Failure>(result)
        assertEquals(AgentErrorCode.SUBSCRIPTION_NOT_FOUND, failure.code)
    }

    @Test
    fun refreshSubscription_fetchesUsingStoredUrl() = runBlocking {
        val subscriptions = FakeSubscriptionRepository(
            listOf(Subscription(id = 3, mark = "work", url = "https://example.invalid/sub")),
        )
        val catalog = XrayAgentCatalog(FakeNodeRepository(), subscriptions)

        val result = catalog.refreshSubscription(3)

        assertIs<AgentActionResult.Success>(result)
        assertEquals(
            listOf("https://example.invalid/sub" to 3),
            subscriptions.fetchCalls,
        )
    }

    @Test
    fun refreshSubscription_fetchThrows_returnsNetworkError() = runBlocking {
        val subscriptions = FakeSubscriptionRepository(
            listOf(Subscription(id = 3, mark = "work", url = "https://example.invalid/sub")),
            fetchError = IllegalStateException("offline"),
        )
        val catalog = XrayAgentCatalog(FakeNodeRepository(), subscriptions)

        val result = catalog.refreshSubscription(3)

        val failure = assertIs<AgentActionResult.Failure>(result)
        assertEquals(AgentErrorCode.NETWORK_ERROR, failure.code)
        assertTrue(failure.message.contains("offline"))
    }

    private fun catalog(
        nodes: List<Node> = emptyList(),
        subscriptions: List<Subscription> = emptyList(),
    ): XrayAgentCatalog = XrayAgentCatalog(
        nodeRepository = FakeNodeRepository(nodes),
        subscriptionRepository = FakeSubscriptionRepository(subscriptions),
    )
}

private fun testNode(
    id: Int,
    remark: String = "manual-$id",
    protocolPrefix: String = "vless://",
    address: String = "example.com",
    port: Int = 443,
    selected: Boolean = false,
    favorite: Boolean = false,
    subscriptionId: Int = AgentNodeFilter.MANUAL_SUBSCRIPTION_ID,
    url: String = "vless://secret-uuid-$id@example.com:443",
    jsonData: String? = """{"id":"secret-uuid-$id"}""",
    countryISO: String = "US",
): Node = Node(
    id = id,
    protocolPrefix = protocolPrefix,
    address = address,
    port = port,
    selected = selected,
    remark = remark,
    subscriptionId = subscriptionId,
    favorite = favorite,
    jsonData = jsonData,
    url = url,
    countryISO = countryISO,
)

private class FakeNodeRepository(
    initial: List<Node> = emptyList(),
) : NodeRepository {
    private val nodes = MutableStateFlow(initial)

    fun current(): List<Node> = nodes.value

    override val allNodes: Flow<List<Node>> = nodes
    override val favorites: Flow<List<Node>> = nodes.map { list -> list.filter { it.favorite } }

    override suspend fun addNode(vararg nodes: Node) {
        this.nodes.value = this.nodes.value + nodes
    }

    override suspend fun deleteLink(link: Node) {
        nodes.value = nodes.value.filterNot { it.id == link.id }
    }

    override fun loadLinksById(id: Int): Flow<Node?> =
        nodes.map { list -> list.find { it.id == id } }

    override suspend fun clearSelection() {
        nodes.value = nodes.value.map { it.copy(selected = false) }
    }

    override fun querySelectedNode(): Flow<Node?> =
        nodes.map { list -> list.find { it.selected } }

    override fun queryPreNode(): Flow<Node?> =
        nodes.map { list -> list.find { it.isPreNode } }

    override fun queryNextNode(): Flow<Node?> =
        nodes.map { list -> list.find { it.isNextNode } }

    override suspend fun updateNode(id: Int, url: String, port: Int, remark: String?) {
        nodes.value = nodes.value.map { node ->
            if (node.id == id) node.copy(url = url, port = port, remark = remark) else node
        }
    }

    override suspend fun updateSelectById(id: Int, selected: Boolean) {
        nodes.value = nodes.value.map { node ->
            if (node.id == id) node.copy(selected = selected) else node
        }
    }

    override suspend fun updateFavoriteById(id: Int, favorite: Boolean) {
        nodes.value = nodes.value.map { node ->
            if (node.id == id) node.copy(favorite = favorite) else node
        }
    }

    override suspend fun deleteLinkById(id: Int) {
        nodes.value = nodes.value.filterNot { it.id == id }
    }

    override suspend fun deleteLinkBySubscriptionId(subscriptionId: Int) {
        nodes.value = nodes.value.filterNot { it.subscriptionId == subscriptionId }
    }

    override suspend fun deleteAllNodes() {
        nodes.value = emptyList()
    }
}

private class FakeSubscriptionRepository(
    initial: List<Subscription> = emptyList(),
    private val fetchError: Throwable? = null,
) : SubscriptionRepository {
    private val subscriptions = MutableStateFlow(initial)
    val fetchCalls = mutableListOf<Pair<String, Int>>()

    override val allSubscriptions: Flow<List<Subscription>> = subscriptions

    override suspend fun addSubscription(subscription: Subscription): Long {
        subscriptions.value = subscriptions.value + subscription
        return subscription.id.toLong()
    }

    override suspend fun deleteSubscription(subscription: Subscription) {
        subscriptions.value = subscriptions.value.filterNot { it.id == subscription.id }
    }

    override suspend fun updateSubscription(subscription: Subscription) {
        subscriptions.value = subscriptions.value.map { current ->
            if (current.id == subscription.id) subscription else current
        }
    }

    override fun getSubscriptionById(id: Int): Flow<Subscription?> =
        subscriptions.map { list -> list.find { it.id == id } }

    override suspend fun fetchAndSaveNodes(
        url: String,
        subscriptionId: Int,
        extraHeaders: Map<String, String>,
    ): com.android.xrayfa.model.SubscriptionMeta {
        fetchError?.let { throw it }
        fetchCalls += url to subscriptionId
        return com.android.xrayfa.model.SubscriptionMeta(
            announce = null,
            profileTitle = null,
            profileUpdateIntervalHours = null,
            profileWebPageUrl = null,
            routing = null,
            routingEnable = null,
            supportUrl = null,
            servedBy = null,
            userInfo = null,
        )
    }
}
