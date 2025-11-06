package com.android.xrayfa.model

import androidx.compose.ui.graphics.Color
import com.android.xrayfa.dto.Link
import com.android.xrayfa.model.protocol.Protocol
import com.android.xrayfa.parser.ShadowSocksConfigParser.ShadowSocksConfig
import com.android.xrayfa.utils.ColorMap

data class Node(
    val id: Int = 0,
    val protocol: Protocol,
    val address: String,  // IP or domain
    val port: Int,
    val selected: Boolean = false,
    val remark: String? = null,
    val color: Color
)
