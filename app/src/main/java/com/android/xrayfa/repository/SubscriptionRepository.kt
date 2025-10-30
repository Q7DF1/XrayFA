package com.android.xrayfa.repository

import com.android.xrayfa.dao.SubscriptionDao
import com.android.xrayfa.dto.Subscription
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SubscriptionRepository
@Inject constructor(
    val subscriptionDao: SubscriptionDao
){

    val allSubscriptions = subscriptionDao.getALLSubscriptions()


    suspend fun addSubscription(subscription: Subscription) {
        subscriptionDao.addSubscription(subscription)
    }

    suspend fun deleteSubscription(subscription: Subscription) {
        subscriptionDao.deleteSubscription(subscription)
    }

    suspend fun updateSubscription(subscription: Subscription) {
        subscriptionDao.updateSubscription(subscription)
    }
}