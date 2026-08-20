package com.android.xrayfa.parser

import com.android.xrayfa.common.utils.Base64Compat

internal object ShareLinks {
    const val VLESS =
        "vless://00000000-0000-0000-0000-000000000001@example.com:443" +
            "?type=raw&security=reality&fp=chrome&pbk=public-key&sni=example.com&sid=abcd&encryption=none" +
            "#vless-node"

    const val TROJAN = "trojan://password@example.com:443?type=tcp&security=tls#trojan-node"

    const val HYSTERIA2 = "hysteria2://auth-token@example.com:443?alpn=h3#h2-node"

    const val SOCKS = "socks://user:pass@example.com:1080#socks-node"

    const val HTTP = "http://user:pass@example.com:8080#http-node"

    val vmess: String
        get() {
            val payload =
                """{"v":"2","ps":"vmess-node","add":"example.com","port":443,"id":"00000000-0000-0000-0000-000000000001","aid":0,"scy":"auto","net":"ws","type":"none","host":"example.com","path":"/ws","tls":"tls"}"""
            return "vmess://${Base64Compat.encode(payload.encodeToByteArray())}"
        }

    val shadowsocks: String
        get() {
            val userInfo = Base64Compat.encode("aes-256-gcm:password".encodeToByteArray())
            return "ss://$userInfo@example.com:8388#ss-node"
        }
}
