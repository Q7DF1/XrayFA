package com.android.xrayfa.dto

import androidx.compose.ui.graphics.Color
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.android.xrayfa.model.protocol.Protocol

@Entity
data class Node(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val protocolPrefix: String,
    val address: String,  // IP or domain
    val port: Int,
    val selected: Boolean = false,
    val remark: String? = null,
    val subscriptionId: Int,
    val url: String,
    val countryISO: String = ""
)