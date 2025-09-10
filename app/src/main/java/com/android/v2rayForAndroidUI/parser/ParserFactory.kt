package com.android.v2rayForAndroidUI.parser

import com.android.v2rayForAndroidUI.model.protocol.Protocol

object ParserFactory {

    fun getParser(protocol: String): AbstractConfigParser {
        return when(protocol) {
            Protocol.VLESS.name.lowercase() -> {
                return VLESSConfigParser()
            }else -> {
                throw IllegalArgumentException("Unsupported protocol: $protocol")
            }
        }
    }
}