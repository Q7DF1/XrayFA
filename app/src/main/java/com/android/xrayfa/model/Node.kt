package com.android.xrayfa.model

import com.android.xrayfa.model.protocol.Protocol

data class Node(
    val id: Int = 0,
    val protocol: Protocol,
    val address: String,  // IP or domain
    val port: Int,
    val selected: Boolean = false,
    val remark:String? = null
)
