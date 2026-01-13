package com.android.xrayfa.parser

import com.android.xrayfa.model.protocol.Protocol
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A simple factory for parsers that provides different parsers for different protocols
 */
@Singleton
class ParserFactory @Inject constructor(
    val vlessConfigParser: VLESSConfigParser,
    val vmessConfigParser: VMESSConfigParser,
    val trojanConfigParser: TrojanConfigParser,
    val shadowSocksConfigParser: ShadowSocksConfigParser
) {

    fun getParser(protocol: String): AbstractConfigParser<*> {
        return when(protocol) {
            Protocol.VLESS.protocolType -> vlessConfigParser
            Protocol.VMESS.protocolType -> vmessConfigParser
            Protocol.TROJAN.protocolType -> trojanConfigParser
            Protocol.SHADOW_SOCKS.protocolType -> shadowSocksConfigParser

            else -> {
                throw IllegalArgumentException("Unsupported protocol: $protocol")
            }
        }
    }
}