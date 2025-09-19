package com.android.v2rayForAndroidUI.model

import com.android.v2rayForAndroidUI.model.protocol.Protocol

data class Node(
    val id: Int = 0,
    val protocol: Protocol,
    val address: String,  // IP or domain
    val port: Int,
    val selected: Boolean = false,
    val remark:String? = null
)
