package com.android.xrayfa.dto

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "Subscription")
data class SubscriptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = -1,
    val mark: String,
    val url: String,
    val preNodeId: Int = -1,
    val nextNodeId: Int = -1,
    val isAutoUpdate: Boolean = false,
)
