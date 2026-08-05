package com.android.xrayfa.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.android.xrayfa.dto.SubscriptionEntity
import kotlinx.coroutines.flow.Flow


@Dao
interface SubscriptionDao {

    @Query("SELECT * FROM subscription")
    fun getALLSubscriptions(): Flow<List<SubscriptionEntity>>

    @Insert
    suspend fun addSubscription(subscription: SubscriptionEntity): Long

    @Delete
    suspend fun deleteSubscription(subscription: SubscriptionEntity)

    @Update
    suspend fun updateSubscription(subscription: SubscriptionEntity)

    @Query("SELECT * FROM subscription WHERE id = :id")
    fun selectSubscriptionById(id: Int): Flow<SubscriptionEntity?>
}
