package com.android.xrayfa.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.android.xrayfa.dto.Link
import com.android.xrayfa.dto.Subscription
import kotlinx.coroutines.flow.Flow


@Dao
interface LinkDao {

    @Query("SELECT * FROM link")
    fun getAllLinks(): Flow<List<Link>>


    @Query("SELECT * FROM link WHERE id = :id")
    fun loadLinksById(id: Int): Flow<Link>

    @Insert
    suspend fun addLink(vararg link: Link)

    @Delete
    suspend fun deleteLink(link: Link)

    @Query("DELETE FROM link WHERE id = :id")
    suspend fun deleteLinkById(id: Int)

    @Update()
    suspend fun updateLink(link: Link)

    @Query("SELECT * FROM link WHERE selected = 1 LIMIT 1")
    fun querySelectedLink(): Flow<Link?>

    @Query("UPDATE link SET selected = :selected WHERE id = :id")
    suspend fun updateLinkById(id: Int,selected: Boolean)


    @Query("UPDATE link SET selected = 0 WHERE selected = 1")
    suspend fun clearSelection()

    @Query("SELECT * FROM link WHERE subscriptionId = :subscriptionId")
    suspend fun queryLinkBySubscriptionId(subscriptionId: Int): List<Link>

    @Query("SELECT * FROM link ORDER BY :subscriptionId ASC")
    suspend fun getAllLinksSortBySubscriptionId(subscriptionId: Int): List<Link>

    @Query("DELETE FROM link WHERE subscriptionId = :subscriptionId")
    suspend fun deleteBySubscriptionId(subscriptionId: Int)
}