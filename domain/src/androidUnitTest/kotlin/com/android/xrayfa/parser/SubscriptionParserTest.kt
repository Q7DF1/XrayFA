package com.android.xrayfa.parser

import org.junit.Assert.assertEquals
import org.junit.Test
import com.android.xrayfa.common.utils.Base64Compat

class SubscriptionParserTest {

    private val parser = SubscriptionParser()

    @Test
    fun parseUrl_decodesBase64AndSplitsLines() {
        val payload = Base64Compat.encode("vless://a\n\nvmess://b\n".encodeToByteArray())
        assertEquals(listOf("vless://a", "vmess://b"), parser.parseUrl(payload))
    }
}
