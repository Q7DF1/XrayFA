package com.android.xrayfa.parser

/** Full [AbstractConfigParser.parse] snapshot for a VLESS share link with GLOBAL routing. */
internal const val VLESS_FULL_CONFIG_GOLDEN = """
{
    "log": {
        "logLevel": "warning"
    },
    "api": {
        "tag": "api",
        "services": [
            "StatsService"
        ]
    },
    "dns": {
        "hosts": {
            "domain:googleapis.cn": "googleapis.com"
        },
        "servers": [
            "1.1.1.1",
            "8.8.8.8"
        ],
        "queryStrategy": "UseIPv4",
        "tag": "dns_inbound"
    },
    "routing": {
        "domainStrategy": "IPIfNonMatch",
        "rules": [
            {
                "inboundTag": [
                    "api"
                ],
                "outboundTag": "api",
                "type": "field"
            },
            {
                "port": "443",
                "network": "udp",
                "outboundTag": "block",
                "type": "field"
            },
            {
                "ip": [
                    "geoip:private"
                ],
                "outboundTag": "direct",
                "type": "field"
            },
            {
                "domain": [
                    "geosite:private"
                ],
                "outboundTag": "direct",
                "type": "field"
            },
            {
                "port": "0-65535",
                "outboundTag": "proxy",
                "type": "field"
            }
        ]
    },
    "policy": {
        "system": {
            "statsInboundUplink": true,
            "statsInboundDownlink": true,
            "statsOutboundUplink": true,
            "statsOutboundDownlink": true
        }
    },
    "inbounds": [
        {
            "listen": "127.0.0.1",
            "port": 10808,
            "protocol": "socks",
            "settings": {
                "auth": "password",
                "accounts": [
                    {
                        "user": "xrayfa",
                        "pass": "xrayfa"
                    }
                ],
                "userLevel": 8,
                "udp": true
            },
            "tag": "socks",
            "sniffing": {
                "enabled": true,
                "destOverride": [
                    "http",
                    "tls"
                ],
                "metadataOnly": false,
                "routeOnly": false
            }
        },
        {
            "listen": "127.0.0.1",
            "port": 10085,
            "protocol": "dokodemo-door",
            "settings": {
                "address": "127.0.0.1"
            },
            "tag": "api"
        },
        {
            "port": 0,
            "protocol": "tun",
            "settings": {
                "name": "xray0",
                "MTU": 1500,
                "userLevel": 8
            },
            "tag": "tun",
            "sniffing": {
                "enabled": true,
                "destOverride": [
                    "http",
                    "tls"
                ],
                "metadataOnly": false,
                "routeOnly": false
            }
        }
    ],
    "outbounds": [
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
        },
        {
            "sendThrough": "0.0.0.0",
            "protocol": "freedom",
            "settings": {},
            "tag": "direct"
        },
        {
            "sendThrough": "0.0.0.0",
            "protocol": "dns",
            "settings": {},
            "tag": "dns-out"
        },
        {
            "sendThrough": "0.0.0.0",
            "protocol": "freedom",
            "settings": {},
            "tag": "api"
        },
        {
            "sendThrough": "0.0.0.0",
            "protocol": "blackhole",
            "settings": {},
            "tag": "block"
        }
    ],
    "stats": {}
}
"""
