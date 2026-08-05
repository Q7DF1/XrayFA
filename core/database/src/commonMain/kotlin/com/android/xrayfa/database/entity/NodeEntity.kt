package com.android.xrayfa.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "Node")
data class NodeEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val protocolPrefix: String,
    val address: String,
    val port: Int,
    val selected: Boolean = false,
    val isPreNode: Boolean = false,
    val isNextNode: Boolean = false,
    val remark: String? = null,
    val subscriptionId: Int,
    val favorite: Boolean = false,
    val jsonData: String? = null,
    val url: String,
    val countryISO: String = "",
)
