package com.android.xrayfa.repository

import com.android.xrayfa.model.Node
import kotlinx.coroutines.flow.Flow

interface NodeRepository {
    val allNodes: Flow<List<Node>>
    val favorites: Flow<List<Node>>

    suspend fun addNode(vararg nodes: Node)
    suspend fun deleteLink(link: Node)
    fun loadLinksById(id: Int): Flow<Node?>
    suspend fun clearSelection()
    fun querySelectedNode(): Flow<Node?>
    fun queryPreNode(): Flow<Node?>
    fun queryNextNode(): Flow<Node?>
    suspend fun updateNode(id: Int, url: String, port: Int, remark: String?)
    suspend fun updateSelectById(id: Int, selected: Boolean)
    suspend fun updateFavoriteById(id: Int, favorite: Boolean)
    suspend fun deleteLinkById(id: Int)
    suspend fun deleteLinkBySubscriptionId(subscriptionId: Int)
    suspend fun deleteAllNodes()
}
