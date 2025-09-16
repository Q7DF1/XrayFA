package com.android.v2rayForAndroidUI.model.protocol

import com.android.v2rayForAndroidUI.model.protocol.Protocol.SHADOW_SOCKS
import com.android.v2rayForAndroidUI.model.protocol.Protocol.TROJAN
import com.android.v2rayForAndroidUI.model.protocol.Protocol.VLESS
import com.android.v2rayForAndroidUI.model.protocol.Protocol.VMESS

enum class Protocol(
    name: String
) {
    VLESS(name = "vless"),

    VMESS(name = "vmess"),

    SHADOW_SOCKS(name = "ss"),

    TROJAN(name = "trojan");


}
val protocols = listOf(
    VLESS.name.lowercase(),
    VMESS.name.lowercase(),
    SHADOW_SOCKS.name.lowercase(),
    TROJAN.name.lowercase()
)
