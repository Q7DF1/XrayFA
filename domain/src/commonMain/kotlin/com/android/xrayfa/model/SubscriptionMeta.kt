package com.android.xrayfa.model

data class SubscriptionUserInfo(
    val upload: Long,
    val download: Long,
    val total: Long,
    val expire: Long?,
)

data class SubscriptionMeta(
    val announce: String?,
    val profileTitle: String?,
    val profileUpdateIntervalHours: Int?,
    val profileWebPageUrl: String?,
    val routing: String?,
    val routingEnable: Boolean?,
    val supportUrl: String?,
    val servedBy: String?,
    val userInfo: SubscriptionUserInfo?,
)
