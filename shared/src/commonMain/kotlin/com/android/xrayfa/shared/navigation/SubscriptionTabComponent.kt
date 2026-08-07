package com.android.xrayfa.shared.navigation

import com.android.xrayfa.model.Subscription
import com.arkivanov.decompose.value.Value

interface SubscriptionComponent {
    val state: Value<SubscriptionState>

    fun isMarkDuplicate(
        mark: String,
        excludeSubscriptionId: Int = 0,
    ): Boolean

    fun openAddSheet()

    fun openEditSheet(subscriptionId: Int)

    fun closeSheet()

    fun showDeleteDialog(subscription: Subscription)

    fun dismissDeleteDialog()

    fun confirmDelete()

    fun addOrUpdateSubscription(
        subscription: Subscription,
        onSuccess: (Int) -> Unit = {},
    )

    fun refreshSubscription(
        subscription: Subscription,
        onSuccess: (Int) -> Unit = {},
    )
}

/** Typealias for tab naming consistency with other Decompose components. */
typealias SubscriptionTabComponent = SubscriptionComponent
