package com.android.xrayfa.dto

import androidx.room.Entity
import androidx.room.PrimaryKey
@Deprecated("use Node instead")
@Entity
data class Link(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val protocolPrefix: String,
    val content: String,
    val subscriptionId: Int,
    val selected: Boolean = false,
)