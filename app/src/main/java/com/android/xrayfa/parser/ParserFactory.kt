package com.android.xrayfa.parser

import com.android.xrayfa.model.protocol.Protocol
import com.android.xrayfa.model.OutboundObject

/**
 * A simple factory for parsers that provides different parsers for different protocols
 */
class ParserFactory(
    val vlessConfigParser: VLESSConfigParser,
    val vmessConfigParser: VMESSConfigParser,
    val trojanConfigParser: TrojanConfigParser,
    val shadowSocksConfigParser: ShadowSocksConfigParser,
    val hysteria2ConfigParser: Hysteria2ConfigParser,
    val socksConfigParser: SocksConfigParser,
    val httpConfigParser: HttpConfigParser
) {

    fun getParser(url: String): AbstractConfigParser<*,*> {
        val parser =  when(val protocol = url.substringBefore("://").lowercase()) {
            Protocol.VLESS.protocolType -> vlessConfigParser
            Protocol.VMESS.protocolType -> vmessConfigParser
            Protocol.TROJAN.protocolType -> trojanConfigParser
            Protocol.SHADOWSOCKS.protocolType -> shadowSocksConfigParser
            Protocol.HYSTERIA2.protocolType -> hysteria2ConfigParser
            Protocol.SOCKS.protocolType -> socksConfigParser
            "socks5" -> socksConfigParser
            Protocol.HTTP.protocolType -> httpConfigParser
            else -> {
                throw IllegalArgumentException("Unsupported protocol: $protocol")
            }
        }
        parser.otherProtocolParser = { url ->
            getParser(url).parseOutbound(url)
        }
        return parser
    }
}