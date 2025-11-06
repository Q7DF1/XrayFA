package com.android.xrayfa.parser

import java.util.Base64

class SubscriptionParser {


    fun parseUrl(content: String): List<String> {
        val decode = String(Base64.getDecoder().decode(content))
        val urls = decode.split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        return urls
    }

}