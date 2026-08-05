package com.android.xrayfa.repository

import com.android.xrayfa.dao.NodeDao
import com.android.xrayfa.dto.toDomain
import com.android.xrayfa.dto.toEntity
import com.android.xrayfa.model.Node
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class NodeRepository(
    private val nodeDao: NodeDao,
) {
    val allNodes: Flow<List<Node>> = nodeDao.getAllNodes().map { nodes -> nodes.map { it.toDomain() } }

    val favorites: Flow<List<Node>> =
        nodeDao.getNodesSelectByFavorite(true).map { nodes -> nodes.map { it.toDomain() } }

    suspend fun addNode(vararg nodes: Node) {
        nodeDao.addNode(*nodes.map { it.toEntity() }.toTypedArray())
    }

    suspend fun deleteLink(link: Node) {
        nodeDao.deleteNode(link.toEntity())
    }

    fun loadLinksById(id: Int): Flow<Node?> {
        return nodeDao.loadNodeById(id).map { it?.toDomain() }
    }

    suspend fun clearSelection() {
        nodeDao.clearSelection()
    }

    fun querySelectedNode(): Flow<Node?> {
        return nodeDao.querySelectedNode().map { it?.toDomain() }
    }

    fun queryPreNode(): Flow<Node?> {
        return nodeDao.queryPreNode().map { it?.toDomain() }
    }

    fun queryNextNode(): Flow<Node?> {
        return nodeDao.queryNextNode().map { it?.toDomain() }
    }

    suspend fun updateNode(id: Int, url: String, port: Int, remark: String?) {
        nodeDao.updateNode(id, url, port, remark)
    }

    suspend fun updateSelectById(id: Int, selected: Boolean) {
        nodeDao.updateSelectById(id, selected)
    }

    suspend fun updateFavoriteById(id: Int, favorite: Boolean) {
        nodeDao.updateFavoriteById(id, favorite)
    }

    suspend fun deleteLinkById(id: Int) {
        nodeDao.deleteNodeById(id)
    }

    suspend fun deleteLinkBySubscriptionId(subscriptionId: Int) {
        nodeDao.deleteBySubscriptionId(subscriptionId)
    }

    suspend fun deleteAllNodes() {
        nodeDao.deleteAll()
    }
}
