package com.android.xrayfa.repository

import com.android.xrayfa.dao.NodeDao
import com.android.xrayfa.dto.Node
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NodeRepository @Inject constructor(
    private val nodeDao: NodeDao
){
    val allNodes = nodeDao.getAllNodes()

    val favorites = nodeDao.getNodesSelectByFavorite(true)

    suspend fun addNode(vararg links: Node) {
        nodeDao.addNode(*links)
    }

    suspend fun deleteLink(link: Node) {
        nodeDao.deleteNode(link)
    }

    fun loadLinksById(id: Int): Flow<Node?> {
        return nodeDao.loadNodeById(id)
    }
    suspend fun clearSelection() {
        return nodeDao.clearSelection()
    }

     fun querySelectedNode(): Flow<Node?> {
        return nodeDao.querySelectedNode()
     }

    fun queryPreNode(): Flow<Node?> {
        return nodeDao.queryPreNode()
    }

    fun queryNextNode(): Flow<Node?> {
        return nodeDao.queryNextNode()
    }

    suspend fun updateNode(id: Int, url: String, port: Int, remark: String?) {
        return  nodeDao.updateNode(id,url,port,remark)
    }

    suspend fun updateSelectById(id: Int, selected: Boolean) {
        return nodeDao.updateSelectById(id,selected)
    }

    suspend fun updateFavoriteById(id: Int, favorite: Boolean) {
        return nodeDao.updateFavoriteById(id,favorite)
    }

    suspend fun deleteLinkById(id: Int) {
        return nodeDao.deleteNodeById(id)
    }

    suspend fun deleteLinkBySubscriptionId(subscriptionId: Int) {
        return nodeDao.deleteBySubscriptionId(subscriptionId)
    }

    suspend fun deleteAllNodes() {
        return nodeDao.deleteAll()
    }
}