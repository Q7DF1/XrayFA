package com.android.xrayfa.parser

import android.net.Uri
import com.android.xrayfa.model.protocol.Protocol
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.net.toUri
import com.android.xrayfa.model.OutboundObject

/**
 * A simple factory for parsers that provides different parsers for different protocols
 */
@Singleton
class ParserFactory @Inject constructor(
    val vlessConfigParser: VLESSConfigParser,
    val vmessConfigParser: VMESSConfigParser,
    val trojanConfigParser: TrojanConfigParser,
    val shadowSocksConfigParser: ShadowSocksConfigParser,
    val hysteria2ConfigParser: Hysteria2ConfigParser
) {

    fun getParser(url: String): AbstractConfigParser<*,*> {
        val parser =  when(val protocol = url.toUri().scheme) {
            Protocol.VLESS.protocolType -> vlessConfigParser
            Protocol.VMESS.protocolType -> vmessConfigParser
            Protocol.TROJAN.protocolType -> trojanConfigParser
            Protocol.SHADOW_SOCKS.protocolType -> shadowSocksConfigParser
            Protocol.HYSTERIA2.protocolType -> hysteria2ConfigParser
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