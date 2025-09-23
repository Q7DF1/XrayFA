package com.android.v2rayForAndroidUI.parser

import com.android.v2rayForAndroidUI.model.protocol.Protocol

object ParserFactory {

    fun getParser(protocol: String): AbstractConfigParser {
        return when(protocol) {
            Protocol.VLESS.protocolName -> {
                return VLESSConfigParser()

            }
            Protocol.VMESS.protocolName -> {
                return VMESSConfigParser()
            }
            Protocol.TROJAN.protocolName -> {
                return TrojanConfigParser()
            }
            Protocol.SHADOW_SOCKS.protocolName -> {
                return ShadowSocksConfigParser()
            }

            else -> {
                throw IllegalArgumentException("Unsupported protocol: $protocol")
            }
        }
    }
}