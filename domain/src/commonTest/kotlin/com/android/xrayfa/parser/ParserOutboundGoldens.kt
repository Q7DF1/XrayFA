package com.android.xrayfa.parser

/**
 * kotlinx.serialization snapshots of [AbstractConfigParser.parseOutbound].
 * Recapture by encoding a share link with [ParserTestFixtures.encodeOutbound].
 */
internal object ParserOutboundGoldens {
    const val VLESS = """
{
    "sendThrough": "0.0.0.0",
    "protocol": "vless",
    "settings": {
        "vnext": [
            {
                "address": "example.com",
                "port": 443,
                "users": [
                    {
                        "id": "00000000-0000-0000-0000-000000000001",
                        "encryption": "none",
                        "flow": "",
                        "level": 0,
                        "security": "auto"
                    }
                ]
            }
        ]
    },
    "tag": "proxy",
    "streamSettings": {
        "network": "raw",
        "security": "reality",
        "realitySettings": {
            "show": false,
            "fingerprint": "chrome",
            "serverName": "example.com",
            "publicKey": "public-key",
            "allowInsecure": false,
            "shortId": "abcd",
            "spiderX": ""
        },
        "rawSettings": {
            "header": {
                "type": "none"
            }
        }
    },
    "mux": {
        "enable": false,
        "concurrency": -1,
        "xudpConcurrency": 8,
        "xudpProxyUDP443": ""
    }
}
"""

    const val VMESS = """
{
    "sendThrough": "0.0.0.0",
    "protocol": "vmess",
    "settings": {
        "vnext": [
            {
                "address": "example.com",
                "port": 443,
                "users": [
                    {
                        "id": "00000000-0000-0000-0000-000000000001",
                        "encryption": "none",
                        "level": 8,
                        "security": "auto"
                    }
                ]
            }
        ]
    },
    "tag": "proxy",
    "streamSettings": {
        "network": "ws",
        "security": "tls",
        "tlsSettings": {
            "serverName": "example.com",
            "allowInsecure": false
        },
        "wsSettings": {
            "path": "/ws",
            "host": "",
            "headers": {
                "host": "example.com"
            },
            "heartbeatPeriod": 0
        }
    }
}
"""

    const val TROJAN = """
{
    "sendThrough": "0.0.0.0",
    "protocol": "trojan",
    "settings": {
        "servers": [
            {
                "address": "example.com",
                "port": 443,
                "password": "password"
            }
        ]
    },
    "tag": "proxy",
    "streamSettings": {
        "network": "tcp",
        "security": "tls",
        "tlsSettings": {
            "serverName": "example.com",
            "allowInsecure": false
        }
    }
}
"""

    const val SHADOWSOCKS = """
{
    "sendThrough": "0.0.0.0",
    "protocol": "shadowsocks",
    "settings": {
        "servers": [
            {
                "address": "example.com",
                "port": 8388,
                "method": "aes-256-gcm",
                "password": "password",
                "uot": false
            }
        ]
    },
    "tag": "proxy",
    "streamSettings": {
        "network": "tcp",
        "security": "none"
    }
}
"""

    const val HYSTERIA2 = """
{
    "sendThrough": "0.0.0.0",
    "protocol": "hysteria",
    "settings": {
        "version": 2,
        "address": "example.com",
        "port": 443
    },
    "tag": "proxy",
    "streamSettings": {
        "network": "hysteria",
        "security": "tls",
        "tlsSettings": {
            "serverName": "example.com",
            "allowInsecure": false,
            "alpn": [
                "h3"
            ]
        },
        "hysteriaSettings": {
            "version": 2,
            "auth": "auth-token",
            "udpIdleTimeout": 60,
            "masquerade": {
                "type": "",
                "dir": "",
                "url": "",
                "rewriteHost": false,
                "insecure": false,
                "content": "",
                "headers": {
                    "key": "value"
                },
                "statusCode": 0
            }
        },
        "finalMask": {},
        "sockopt": {}
    }
}
"""

    const val SOCKS = """
{
    "sendThrough": "0.0.0.0",
    "protocol": "socks",
    "settings": {
        "servers": [
            {
                "address": "example.com",
                "port": 1080,
                "users": [
                    {
                        "user": "user",
                        "pass": "pass"
                    }
                ]
            }
        ]
    },
    "tag": "proxy",
    "streamSettings": {
        "network": "tcp",
        "security": "none"
    }
}
"""

    const val HTTP = """
{
    "sendThrough": "0.0.0.0",
    "protocol": "http",
    "settings": {
        "servers": [
            {
                "address": "example.com",
                "port": 8080,
                "users": [
                    {
                        "user": "user",
                        "pass": "pass"
                    }
                ]
            }
        ]
    },
    "tag": "proxy",
    "streamSettings": {
        "network": "tcp",
        "security": "none"
    }
}
"""
}
