package com.android.xrayfa.repository

import com.android.xrayfa.model.Subscription
import com.android.xrayfa.model.SubscriptionMeta
import kotlinx.coroutines.flow.Flow

interface SubscriptionRepository {
    val allSubscriptions: Flow<List<Subscription>>

    suspend fun addSubscription(subscription: Subscription): Long
    suspend fun deleteSubscription(subscription: Subscription)
    suspend fun updateSubscription(subscription: Subscription)
    fun getSubscriptionById(id: Int): Flow<Subscription?>
    suspend fun fetchAndSaveNodes(
        url: String,
        subscriptionId: Int,
        extraHeaders: Map<String, String> = emptyMap(),
    ): SubscriptionMeta
}
