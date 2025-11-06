package com.android.xrayfa.parser

import com.android.xrayfa.model.protocol.Protocol

/**
 * A simple factory for parsers that provides different parsers for different protocols
 */
object ParserFactory {

    fun getParser(protocol: String): AbstractConfigParser<*> {
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