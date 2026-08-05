package com.android.xrayfa.model

/**
 * Subscription metadata — platform-neutral domain model.
 * Room [com.android.xrayfa.dto.SubscriptionEntity] maps at the app data boundary.
 */
data class Subscription(
    val id: Int = -1,
    val mark: String,
    val url: String,
    val preNodeId: Int = -1,
    val nextNodeId: Int = -1,
    val isAutoUpdate: Boolean = false,
)
