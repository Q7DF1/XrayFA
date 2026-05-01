package com.android.xrayfa.model.protocol

import com.android.xrayfa.model.protocol.Protocol.SHADOW_SOCKS
import com.android.xrayfa.model.protocol.Protocol.TROJAN
import com.android.xrayfa.model.protocol.Protocol.VLESS
import com.android.xrayfa.model.protocol.Protocol.VMESS
import com.android.xrayfa.model.protocol.Protocol.HYSTERIA2

/**
 * @param protocolType protocol type == protocolPrefix
 */
enum class Protocol(
    val protocolType: String
) {
    VLESS("vless"),

    VMESS("vmess"),

    SHADOW_SOCKS("ss"),

    TROJAN("trojan"),

    HYSTERIA2("hysteria2");


}
val protocolsPrefix = listOf(
    VLESS.protocolType,
    VMESS.protocolType,
    SHADOW_SOCKS.protocolType,
    TROJAN.protocolType,
    HYSTERIA2.protocolType
)
val protocolPrefixMap = mapOf(
    SHADOW_SOCKS.protocolType to SHADOW_SOCKS,
    VLESS.protocolType to VLESS,
    VMESS.protocolType to VMESS,
    TROJAN.protocolType to TROJAN,
    HYSTERIA2.protocolType to HYSTERIA2
)
