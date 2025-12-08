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
    val allLinks = nodeDao.getAllNodes()

    suspend fun addNode(vararg links: Node) {
        nodeDao.addNode(*links)
    }

    suspend fun deleteLink(link: Node) {
        nodeDao.deleteNode(link)
    }

    fun loadLinksById(id: Int): Flow<Node> {
        return nodeDao.loadNodeById(id)
    }
    suspend fun clearSelection() {
        return nodeDao.clearSelection()
    }

     fun querySelectedLink(): Flow<Node?> {
        return nodeDao.querySelectedNode()
    }

    suspend fun updateNodeUrlAndPort(id: Int,url: String,port: Int) {
        return  nodeDao.updateNodeUrlAndPort(id,url,port)
    }

    suspend fun updateLinkById(id: Int, selected: Boolean) {
        return nodeDao.updateNodeById(id,selected)
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