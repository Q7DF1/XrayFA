package com.android.xrayfa.shared.config

import com.android.xrayfa.config.JsonConfigException
import com.android.xrayfa.model.*
import com.android.xrayfa.repository.*
import com.android.xrayfa.shared.vpn.VpnStartOptionsResolver
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class JsonConfigEditorTest {
    private val raw = """{ "outbounds": [{"protocol":"freedom"}], "custom": {"preserved": true} }"""
    private fun link(id: Int = 1) = Node(id = id, protocolPrefix = "socks", address = "localhost", port = 1080, subscriptionId = 10, selected = true, url = "socks://localhost:1080")

    @Test fun importLeavesExistingSelectionAndLinksUntouched() = runBlocking {
        val old = link()
        val repo = MemoryNodes(listOf(old))
        JsonConfigEditor(repo).save(0, "Imported", raw, null)
        assertEquals(old, repo.nodes.value.first())
        val added = repo.nodes.value.last()
        assertTrue(added.isJsonConfig)
        assertFalse(added.selected)
        assertEquals(raw, added.jsonData)
        assertFalse(added.url.contains("freedom"))
    }

    @Test fun editKeepsIdFavoriteSelectionAndIdentifier() = runBlocking {
        val original = link().copy(protocolPrefix = JSON_CONFIG_PREFIX, url = jsonConfigIdentifier("stable", null), jsonData = raw, favorite = true)
        val repo = MemoryNodes(listOf(original))
        JsonConfigEditor(repo).save(1, "Renamed", raw, "inbound /测试")
        val updated = repo.nodes.value.single()
        assertEquals(original.id, updated.id)
        assertTrue(updated.selected && updated.favorite)
        assertEquals("inbound /测试", updated.jsonInboundTag)
        assertTrue(updated.url.startsWith("json-config://stable?"))
        assertEquals("Renamed", updated.remark)
    }

    @Test fun invalidEditAndWrongRecordTypeNeverWrite() = runBlocking {
        val original = link()
        val repo = MemoryNodes(listOf(original))
        val editor = JsonConfigEditor(repo)
        assertFailsWith<JsonConfigException> { editor.save(1, "x", "{", null) }
        assertFailsWith<JsonConfigException> { editor.save(1, "x", raw, null) }
        assertEquals(listOf(original), repo.nodes.value)
    }

    @Test fun resolverPreservesLinkChainsAndBypassesThemForJson() = runBlocking {
        val selected = link()
        val before = link(2).copy(selected = false, url = "socks://before:1080")
        val after = link(3).copy(selected = false, url = "socks://after:1080")
        val repo = MemoryNodes(listOf(selected, before, after))
        val subs = MemorySubscriptions(Subscription(id = 10, url = "https://example.org", mark = "test", preNodeId = 2, nextNodeId = 3, isAutoUpdate = false))
        val resolver = VpnStartOptionsResolver(repo, subs)
        val normal = resolver.resolve()!!
        assertEquals(before.url, normal.preUrl)
        assertEquals(after.url, normal.nextUrl)
        assertNull(normal.jsonConfig)
        repo.nodes.value = listOf(selected.copy(protocolPrefix = JSON_CONFIG_PREFIX, jsonData = raw, url = jsonConfigIdentifier("raw", "input")), before, after)
        val json = resolver.resolve()!!
        assertEquals(raw, json.jsonConfig)
        assertEquals("input", json.jsonInboundTag)
        assertNull(json.preUrl)
        assertNull(json.nextUrl)
        assertFalse(json.toString().contains("preserved"))
    }
}

private class MemoryNodes(initial: List<Node>) : NodeRepository {
    val nodes = MutableStateFlow(initial)
    override val allNodes = nodes
    override val favorites = nodes.map { it.filter(Node::favorite) }
    override suspend fun addNode(vararg nodes: Node) { this.nodes.value += nodes.map { it.copy(id = this.nodes.value.size + 1) } }
    override suspend fun deleteLink(link: Node) = deleteLinkById(link.id)
    override fun loadLinksById(id: Int) = nodes.map { it.find { node -> node.id == id } }
    override suspend fun clearSelection() { nodes.value = nodes.value.map { it.copy(selected = false) } }
    override fun querySelectedNode() = nodes.map { it.find(Node::selected) }
    override fun queryPreNode() = flowOf<Node?>(null)
    override fun queryNextNode() = flowOf<Node?>(null)
    override suspend fun updateNode(id: Int, url: String, port: Int, remark: String?) { error("Unexpected link mutation") }
    override suspend fun updateJsonConfig(id: Int, identifier: String, remark: String, jsonData: String) {
        nodes.value = nodes.value.map { if (it.id == id) it.copy(url = identifier, remark = remark, jsonData = jsonData) else it }
    }
    override suspend fun updateSelectById(id: Int, selected: Boolean) { nodes.value = nodes.value.map { if (it.id == id) it.copy(selected = selected) else it } }
    override suspend fun updateFavoriteById(id: Int, favorite: Boolean) { nodes.value = nodes.value.map { if (it.id == id) it.copy(favorite = favorite) else it } }
    override suspend fun deleteLinkById(id: Int) { nodes.value = nodes.value.filterNot { it.id == id } }
    override suspend fun deleteLinkBySubscriptionId(subscriptionId: Int) { nodes.value = nodes.value.filterNot { it.subscriptionId == subscriptionId } }
    override suspend fun deleteAllNodes() { nodes.value = emptyList() }
}

private class MemorySubscriptions(private val subscription: Subscription) : SubscriptionRepository {
    override val allSubscriptions = flowOf(listOf(subscription))
    override fun getSubscriptionById(id: Int) = flowOf(subscription.takeIf { it.id == id })
    override suspend fun addSubscription(subscription: Subscription): Long = error("Unexpected write")
    override suspend fun deleteSubscription(subscription: Subscription): Unit = error("Unexpected write")
    override suspend fun updateSubscription(subscription: Subscription): Unit = error("Unexpected write")
    override suspend fun fetchAndSaveNodes(url: String, subscriptionId: Int, extraHeaders: Map<String, String>): SubscriptionMeta = error("Unexpected fetch")
}
