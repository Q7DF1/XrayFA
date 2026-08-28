package com.android.xrayfa.common.utils

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import com.android.xrayfa.common.json.AppJson

class ShareLinkCleanerTest {

    @Test
    fun vless_stripsAllowInsecureKeepsFragment() {
        val url = "vless://id@host:443?encryption=none&security=tls&allowInsecure=1#n"
        assertEquals(
            "vless://id@host:443?encryption=none&security=tls#n",
            ShareLinkCleaner.cleanUrlForSharing(url),
        )
    }

    @Test
    fun hysteria2_stripsAllowInsecure() {
        val url = "hysteria2://secret@host:443?insecure=0&allowInsecure=1"
        assertEquals(
            "hysteria2://secret@host:443?insecure=0",
            ShareLinkCleaner.cleanUrlForSharing(url),
        )
    }

    @Test
    fun vmess_stripsAllowInsecureKeepsOtherFields() {
        val payload = """{"add":"h","port":"443","id":"u","allowInsecure":"1","ps":"t"}"""
        val url = "vmess://${Base64Compat.encode(payload.encodeToByteArray())}"
        val cleaned = ShareLinkCleaner.cleanUrlForSharing(url)
        val decoded = Base64Compat.decode(cleaned.removePrefix("vmess://")).decodeToString()
        val obj = AppJson.parseToJsonElement(decoded).jsonObject
        assertNull(obj["allowInsecure"])
        assertEquals("h", obj["add"]?.jsonPrimitive?.content)
        assertEquals("t", obj["ps"]?.jsonPrimitive?.content)
    }

    @Test
    fun ss_andEmpty_unchanged() {
        assertEquals("ss://abc@host:443#n", ShareLinkCleaner.cleanUrlForSharing("ss://abc@host:443#n"))
        assertEquals("", ShareLinkCleaner.cleanUrlForSharing(""))
    }
}
