package com.android.xrayfa.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.android.xrayfa.dto.Node
import kotlinx.coroutines.flow.Flow


@Dao
interface NodeDao {

    @Query("SELECT * FROM node")
    fun getAllNodes(): Flow<List<Node>>


    @Query("SELECT * FROM node WHERE id = :id")
    fun loadNodeById(id: Int): Flow<Node>

    @Insert
    suspend fun addNode(vararg nodes: Node)

    @Delete
    suspend fun deleteNode(node: Node)

    @Query("DELETE FROM node WHERE id = :id")
    suspend fun deleteNodeById(id: Int)

    @Update()
    suspend fun updateNode(link: Node)

    @Query("SELECT * FROM node WHERE selected = 1 LIMIT 1")
    fun querySelectedNode(): Flow<Node?>

    @Query("UPDATE node SET selected = :selected WHERE id = :id")
    suspend fun updateNodeById(id: Int, selected: Boolean)


    @Query("UPDATE node SET selected = 0 WHERE selected = 1")
    suspend fun clearSelection()

    @Query("SELECT * FROM node WHERE subscriptionId = :subscriptionId")
    suspend fun queryNodeBySubscriptionId(subscriptionId: Int): List<Node>

    @Query("SELECT * FROM node ORDER BY :subscriptionId ASC")
    suspend fun getAllNodesSortBySubscriptionId(subscriptionId: Int): List<Node>

    @Query("DELETE FROM node WHERE subscriptionId = :subscriptionId")
    suspend fun deleteBySubscriptionId(subscriptionId: Int)

    @Query("DELETE FROM node")
    suspend fun deleteAll()
}