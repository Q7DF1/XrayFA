package com.android.xrayfa.config

import com.android.xrayfa.dto.ParseLinkInput
import com.android.xrayfa.model.*
import com.android.xrayfa.parser.ParserTestFixtures
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.*

class XDriveShareLinkTest {
    private val id = "00000000-0000-0000-0000-000000000001"
    private val encryption = "mlkem768x25519plus.native.0rtt.${"A".repeat(43)}"
    private val parser = ParserTestFixtures.factory().vlessConfigParser
    private fun profile(service: String) = XDriveForm(id = id, encryption = encryption,
        service = service, remoteFolder = "private-folder", secrets = listOf("secret-key", "secret-value", "private-token"),
        endpoint = "https://private-storage.example", bucket = "private-bucket", region = "private-region",
        security = "tls", serverName = "storage.example", alpn = "h2", fingerprint = "firefox",
        advanced = if (service == "template") """{"template":{"put":{"url":"https://private.example/put"},"get":{"url":"https://private.example/get"},"list":{"url":"https://private.example/list","namesRegex":"(.*)"},"delete":{"url":"https://private.example/delete"}},"custom":"private-extra"}""" else """{"custom":"private-extra"}""")
    private fun node(form: XDriveForm) = Node(protocolPrefix = JSON_CONFIG_PREFIX, address = "", port = 0,
        subscriptionId = -1, remark = "basic + 名称", url = jsonConfigIdentifier("xdrive-example", null), jsonData = form.build().toString())

    @Test fun addressAndPortRoundTripWithoutChangingStorage() = runBlocking {
        val original = node(profile("s3").copy(address = "192.0.2.10", port = 9000))
        val link = assertNotNull(original.configurationShareLink())
        assertEquals("192.0.2.10", parser.decodeProtocol(link).server)
        assertEquals(9000, parser.decodeProtocol(link).port)
        val imported = parser.preParse(ParseLinkInput(protocolPrefix = "vless", content = link, subscriptionId = -1))
        val form = assertNotNull(XDriveForm.fromJson(imported.jsonData!!))
        assertEquals("192.0.2.10", form.address)
        assertEquals(9000, form.port)
        assertTrue(form.endpoint.isEmpty())
        assertTrue(form.remoteFolder.isEmpty())
    }

    @Test fun allBackendsShareOnlyAllowlistedVlessFields() {
        for (backend in listOf("local", "google-drive", "s3", "template")) {
            val original = node(profile(backend))
            val link = assertNotNull(original.configurationShareLink())
            val decoded = parser.decodeProtocol(link)
            assertEquals(id, decoded.uuid)
            assertEquals("basic + 名称", decoded.remark)
            assertEquals(encryption, decoded.param["encryption"])
            assertEquals(setOf("type", "encryption", "security", "sni", "alpn", "fp"), decoded.param.keys)
            assertEquals("xdrive", decoded.param["type"])
            assertFalse(link.contains("private"))
            assertFalse(link.contains("secret"))
            assertFalse(link.contains("json-config"))
            assertTrue(original.canShareConfiguration)
        }
    }

    @Test fun basicImportCreatesAnUnselectedDraftThatMustBeCompleted() = runBlocking {
        val link = assertNotNull(node(profile("s3")).configurationShareLink())
        val injected = link.replace("#", "&secrets=INJECTED&remoteFolder=INJECTED&extra=INJECTED#")
        val draft = parser.preParse(ParseLinkInput(protocolPrefix = "vless", content = injected, subscriptionId = -1, selected = true))
        assertTrue(draft.isManagedXDrive)
        assertTrue(draft.requiresXDriveSetup)
        assertFalse(draft.selected)
        assertFalse(draft.jsonData!!.contains("INJECTED"))
        val form = assertNotNull(XDriveForm.fromJson(draft.jsonData!!))
        assertEquals(id, form.id)
        assertEquals(encryption, form.encryption)
        assertEquals("storage.example", form.serverName)
        assertEquals("", form.remoteFolder)
        assertTrue(form.secrets.isEmpty())
        assertNotNull(form.editableMuxConfig())
        assertEquals(JsonConfigError.XDRIVE_SETUP_REQUIRED,
            assertFailsWith<JsonConfigException> { draft.requireXDriveReady() }.reason)
        assertEquals(link, draft.configurationShareLink())
        val completed = draft.copy(jsonData = form.copy(service = "local", remoteFolder = "/tmp/test-only").build().toString())
        completed.requireXDriveReady()
        assertFalse(completed.requiresXDriveSetup)
    }

    @Test fun basicLinksCannotBeRunDirectlyOrSilentlyReplacedBySubscriptionRefresh() = runBlocking {
        val link = XDriveShareLink.encode(profile("local"), "basic")
        assertFailsWith<IllegalArgumentException> { parser.parseOutbound(link) }
        assertFailsWith<IllegalArgumentException> {
            parser.preParse(ParseLinkInput(protocolPrefix = "vless", content = link, subscriptionId = 2))
        }
        Unit
    }

    @Test fun genericJsonAndMalformedManagedRecordsRemainUnshareable() {
        val original = node(profile("local"))
        assertNull(original.copy(url = jsonConfigIdentifier("global", null)).configurationShareLink())
        assertNull(original.copy(jsonData = "{}").configurationShareLink())
        assertNull(original.copy(jsonData = null).configurationShareLink())
        assertTrue(original.copy(jsonData = "{}").requiresXDriveSetup)
        assertEquals(JsonConfigError.XDRIVE_SETUP_REQUIRED,
            assertFailsWith<JsonConfigException> { original.copy(jsonData = null).requireXDriveReady() }.reason)
        assertFailsWith<IllegalArgumentException> { XDriveShareLink.encode(profile("local").copy(encryption = "none"), null) }
    }
}
