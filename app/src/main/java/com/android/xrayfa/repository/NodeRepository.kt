package com.android.xrayfa.repository

import com.android.xrayfa.dao.LinkDao
import com.android.xrayfa.dao.NodeDao
import com.android.xrayfa.dto.Link
import com.android.xrayfa.dto.Node
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NodeRepository @Inject constructor(
    private val nodeDao: NodeDao
){
    val allLinks = nodeDao.getAllLinks()

    suspend fun addNode(vararg links: Node) {
        nodeDao.addLink(*links)
    }

    suspend fun deleteLink(link: Node) {
        nodeDao.deleteLink(link)
    }

    fun loadLinksById(id: Int): Flow<Node> {
        return nodeDao.loadLinksById(id)
    }
    suspend fun clearSelection() {
        return nodeDao.clearSelection()
    }

     fun querySelectedLink(): Flow<Node?> {
        return nodeDao.querySelectedLink()
    }

    suspend fun updateLink(node: Node) {
        return  nodeDao.updateLink(node)
    }

    suspend fun updateLinkById(id: Int, selected: Boolean) {
        return nodeDao.updateLinkById(id,selected)
    }

    suspend fun deleteLinkById(id: Int) {
        return nodeDao.deleteLinkById(id)
    }

    suspend fun deleteLinkBySubscriptionId(subscriptionId: Int) {
        return nodeDao.deleteBySubscriptionId(subscriptionId)
    }
}