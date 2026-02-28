package com.android.xrayfa.model.protocol

import com.android.xrayfa.model.protocol.Protocol.SHADOW_SOCKS
import com.android.xrayfa.model.protocol.Protocol.TROJAN
import com.android.xrayfa.model.protocol.Protocol.VLESS
import com.android.xrayfa.model.protocol.Protocol.VMESS

/**
 * @param protocolType protocol type
 */
enum class Protocol(
    val protocolType: String
) {
    VLESS("vless"),

    VMESS("vmess"),

    SHADOW_SOCKS("ss"),

    TROJAN("trojan");


}
val protocolsPrefix = listOf(
    VLESS.protocolType,
    VMESS.protocolType,
    SHADOW_SOCKS.protocolType,
    TROJAN.protocolType
)
val protocolPrefixMap = mapOf(
    SHADOW_SOCKS.protocolType to SHADOW_SOCKS,
    VLESS.protocolType to VLESS,
    VMESS.protocolType to VMESS,
    TROJAN.protocolType to TROJAN
)
