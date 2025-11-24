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
val protocolsPrefix = listOf(
    VLESS.protocolName,
    VMESS.protocolName,
    SHADOW_SOCKS.protocolName,
    TROJAN.protocolName
)
val protocolPrefixMap = mapOf(
    SHADOW_SOCKS.protocolName to SHADOW_SOCKS,
    VLESS.protocolName to VLESS,
    VMESS.protocolName to VMESS,
    TROJAN.protocolName to TROJAN
)
