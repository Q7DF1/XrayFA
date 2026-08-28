package com.android.xrayfa.shared.navigation

import com.android.xrayfa.model.Node
import com.android.xrayfa.model.Subscription

val EmptySubscription = Subscription(id = 0, mark = "", url = "", preNodeId = -1, nextNodeId = -1)

data class SubscriptionState(
    val subscriptions: List<Subscription> = emptyList(),
    val allNodes: List<Node> = emptyList(),
    val sheetSubscription: Subscription? = null,
    val deleteTarget: Subscription? = null,
    val requesting: Boolean = false,
    val subscribeError: Boolean = false,
)
