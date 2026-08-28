package com.android.xrayfa.parser

import com.android.xrayfa.common.utils.Base64Compat

/**
 *
 * Parse subscription link
 */
class SubscriptionParser {


    fun parseUrl(content: String): List<String> {
        val decode = Base64Compat.decode(content).decodeToString()
        val urls = decode.split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        return urls
    }

}