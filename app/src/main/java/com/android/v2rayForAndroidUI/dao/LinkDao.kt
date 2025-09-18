package com.android.v2rayForAndroidUI.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.android.v2rayForAndroidUI.model.Link
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
}