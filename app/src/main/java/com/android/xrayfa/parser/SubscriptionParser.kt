package com.android.xrayfa.parser

import com.android.xrayfa.common.utils.Base64Compat

/**
 *
 * Parse subscription link
 */
class SubscriptionParser {


    fun parseUrl(content: String): List<String> {
        val decode = String(Base64Compat.decode(content))
        val urls = decode.split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        return urls
    }

}