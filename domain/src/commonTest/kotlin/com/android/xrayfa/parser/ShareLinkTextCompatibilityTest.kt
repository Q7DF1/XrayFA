package com.android.xrayfa.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ShareLinkTextCompatibilityTest {
    private val base = "vless://test-id@example.invalid:443"
    private fun parse(suffix: String) = ShareLinkUri.parse(base + suffix, setOf("vless"))

    @Test
    fun unescapedDisplayNameIsAcceptedWithoutChangingEndpoint() {
        val remark = "香港 01 | <test> {node} \"label\" \\ ^ `"
        val result = parse("?security=none#$remark")
        assertEquals(remark, result.remark)
        assertEquals("example.invalid", result.address)
        assertEquals(443, result.port)
        assertEquals("test-id", result.credential)
    }

    @Test
    fun rawJsonAndSpacesInQueryValuesAreAccepted() {
        val result = parse("?type=xhttp&extra={\"value\":\"hello world\"}&path=/a b#name")
        assertEquals("{\"value\":\"hello world\"}", result.params["extra"])
        assertEquals("/a b", result.params["path"])
        assertEquals("xhttp", result.params["type"])
    }

    @Test
    fun existingEscapesAndEncodedSeparatorsAreNotEncodedAgain() {
        val result = parse("?path=%2Fa%26b%3Dc&host=example.invalid#Hong%20Kong%2B1")
        assertEquals("/a&b=c", result.params["path"])
        assertEquals("Hong Kong+1", result.remark)
    }

    @Test
    fun unicodeSpaceInRemarkIsAccepted() {
        assertEquals("Hong\u00a0Kong", parse("#Hong\u00a0Kong").remark)
    }

    @Test
    fun malformedAuthorityEscapesAndControlCharactersRemainInvalid() {
        assertFailsWith<IllegalArgumentException> {
            ShareLinkUri.parse("vless://test-id@bad host:443#name", setOf("vless"))
        }
        assertFailsWith<IllegalArgumentException> { parse("#bad%zz") }
        assertFailsWith<IllegalArgumentException> { parse("#bad\nname") }
        assertFailsWith<IllegalArgumentException> { parse("?path=/bad\tpath") }
    }
}
