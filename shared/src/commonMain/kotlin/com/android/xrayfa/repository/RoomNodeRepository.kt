package com.android.xrayfa.repository

import com.android.xrayfa.database.dao.NodeDao
import com.android.xrayfa.dto.toDomain
import com.android.xrayfa.dto.toEntity
import com.android.xrayfa.model.Node
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** KMP Room-backed [NodeRepository] used by Android and iOS Koin. */
class RoomNodeRepository(
    private val nodeDao: NodeDao,
) : NodeRepository {
    override val allNodes: Flow<List<Node>> =
        nodeDao.getAllNodes().map { nodes -> nodes.map { it.toDomain() } }

    override val favorites: Flow<List<Node>> =
        nodeDao.getNodesSelectByFavorite(true).map { nodes -> nodes.map { it.toDomain() } }

    override suspend fun addNode(vararg nodes: Node) {
        nodeDao.addNode(*nodes.map { it.toEntity() }.toTypedArray())
    }

    override suspend fun deleteLink(link: Node) {
        nodeDao.deleteNode(link.toEntity())
    }

    override fun loadLinksById(id: Int): Flow<Node?> {
        return nodeDao.loadNodeById(id).map { it?.toDomain() }
    }

    override suspend fun clearSelection() {
        nodeDao.clearSelection()
    }

    override fun querySelectedNode(): Flow<Node?> {
        return nodeDao.querySelectedNode().map { it?.toDomain() }
    }

    override fun queryPreNode(): Flow<Node?> {
        return nodeDao.queryPreNode().map { it?.toDomain() }
    }

    override fun queryNextNode(): Flow<Node?> {
        return nodeDao.queryNextNode().map { it?.toDomain() }
    }

    override suspend fun updateNode(id: Int, url: String, port: Int, remark: String?) {
        nodeDao.updateNode(id, url, port, remark)
    }

    override suspend fun updateSelectById(id: Int, selected: Boolean) {
        nodeDao.updateSelectById(id, selected)
    }

    override suspend fun updateFavoriteById(id: Int, favorite: Boolean) {
        nodeDao.updateFavoriteById(id, favorite)
    }

    override suspend fun deleteLinkById(id: Int) {
        nodeDao.deleteNodeById(id)
    }

    override suspend fun deleteLinkBySubscriptionId(subscriptionId: Int) {
        nodeDao.deleteBySubscriptionId(subscriptionId)
    }

    override suspend fun deleteAllNodes() {
        nodeDao.deleteAll()
    }
}
