package com.android.xrayfa.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.android.xrayfa.database.entity.NodeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NodeDao {

    @Query("SELECT * FROM node")
    fun getAllNodes(): Flow<List<NodeEntity>>

    @Query("SELECT * FROM node WHERE favorite = :favorite")
    fun getNodesSelectByFavorite(favorite: Boolean): Flow<List<NodeEntity>>

    @Query("SELECT * FROM node WHERE id = :id")
    fun loadNodeById(id: Int): Flow<NodeEntity?>

    @Insert
    suspend fun addNode(vararg nodes: NodeEntity)

    @Delete
    suspend fun deleteNode(node: NodeEntity)

    @Query("DELETE FROM node WHERE id = :id")
    suspend fun deleteNodeById(id: Int)

    @Query("UPDATE node SET url = :url, port = :port, remark = :remark WHERE id = :id")
    suspend fun updateNode(id: Int, url: String, port: Int, remark: String?)

    @Query("SELECT * FROM node WHERE selected = 1 LIMIT 1")
    fun querySelectedNode(): Flow<NodeEntity?>

    @Query("SELECT * FROM node WHERE isPreNode = 1 LIMIT 1")
    fun queryPreNode(): Flow<NodeEntity?>

    @Query("SELECT * FROM node WHERE isNextNode = 1 LIMIT 1")
    fun queryNextNode(): Flow<NodeEntity?>

    @Query("UPDATE node SET selected = :selected WHERE id = :id")
    suspend fun updateSelectById(id: Int, selected: Boolean)

    @Query("UPDATE node SET favorite = :favorite WHERE id = :id")
    suspend fun updateFavoriteById(id: Int, favorite: Boolean)

    @Query("UPDATE node SET selected = 0 WHERE selected = 1")
    suspend fun clearSelection()

    @Query("SELECT * FROM node WHERE subscriptionId = :subscriptionId")
    suspend fun queryNodeBySubscriptionId(subscriptionId: Int): List<NodeEntity>

    @Query("SELECT * FROM node WHERE subscriptionId = :subscriptionId ORDER BY id ASC")
    suspend fun getAllNodesSortBySubscriptionId(subscriptionId: Int): List<NodeEntity>

    @Query("DELETE FROM node WHERE subscriptionId = :subscriptionId")
    suspend fun deleteBySubscriptionId(subscriptionId: Int)

    @Query("DELETE FROM node")
    suspend fun deleteAll()
}
