package com.android.xrayfa.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.android.xrayfa.dto.Link
import com.android.xrayfa.dto.Node
import com.android.xrayfa.dto.Subscription
import kotlinx.coroutines.flow.Flow


@Dao
interface NodeDao {

    @Query("SELECT * FROM node")
    fun getAllLinks(): Flow<List<Node>>


    @Query("SELECT * FROM node WHERE id = :id")
    fun loadLinksById(id: Int): Flow<Node>

    @Insert
    suspend fun addLink(vararg nodes: Node)

    @Delete
    suspend fun deleteLink(node: Node)

    @Query("DELETE FROM node WHERE id = :id")
    suspend fun deleteLinkById(id: Int)

    @Update()
    suspend fun updateLink(link: Node)

    @Query("SELECT * FROM node WHERE selected = 1 LIMIT 1")
    fun querySelectedLink(): Flow<Node?>

    @Query("UPDATE node SET selected = :selected WHERE id = :id")
    suspend fun updateLinkById(id: Int,selected: Boolean)


    @Query("UPDATE node SET selected = 0 WHERE selected = 1")
    suspend fun clearSelection()

    @Query("SELECT * FROM node WHERE subscriptionId = :subscriptionId")
    suspend fun queryLinkBySubscriptionId(subscriptionId: Int): List<Node>

    @Query("SELECT * FROM node ORDER BY :subscriptionId ASC")
    suspend fun getAllLinksSortBySubscriptionId(subscriptionId: Int): List<Node>

    @Query("DELETE FROM node WHERE subscriptionId = :subscriptionId")
    suspend fun deleteBySubscriptionId(subscriptionId: Int)
}