package com.android.xrayfa.parser

import com.android.xrayfa.common.utils.Base64Compat
import kotlin.test.Test
import kotlin.test.assertEquals

class SubscriptionParserTest {

    private val parser = SubscriptionParser()

    @Test
    fun parseUrl_decodesBase64AndSplitsLines() {
        val payload = Base64Compat.encode("vless://a\n\nvmess://b\n".encodeToByteArray())
        assertEquals(listOf("vless://a", "vmess://b"), parser.parseUrl(payload))
    }
}
