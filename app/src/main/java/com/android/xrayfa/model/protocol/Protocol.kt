package com.android.xrayfa.model.protocol

import com.android.xrayfa.model.protocol.Protocol.SHADOW_SOCKS
import com.android.xrayfa.model.protocol.Protocol.TROJAN
import com.android.xrayfa.model.protocol.Protocol.VLESS
import com.android.xrayfa.model.protocol.Protocol.VMESS

enum class Protocol(
    val protocolName: String
) {
    VLESS("vless"),

    VMESS("vmess"),

    SHADOW_SOCKS("ss"),

    TROJAN("trojan");


}
val protocols = listOf(
    VLESS.protocolName,
    VMESS.protocolName,
    SHADOW_SOCKS.protocolName,
    TROJAN.protocolName
)
