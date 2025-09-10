package com.android.v2rayForAndroidUI.model.protocol

enum class Protocol(
    name: String
) {
    VLESS(name = "vless"),

    VMESS(name = "vmess"),

    SHADOW_SOCKS(name = "ss"),

    TROJAN(name = "trojan");
}