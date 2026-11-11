package com.android.xrayfa.config

import com.android.xrayfa.model.*
import kotlinx.serialization.json.*
import kotlin.test.*

class XDriveConfigTest {
    private val dummy = "fixture-credential"
    private val encryption = "mlkem768x25519plus.native.1rtt.${"A".repeat(43)}"
    private fun form(service: String = "google-drive") = XDriveForm(
        id = "00000000-0000-0000-0000-000000000001", encryption = encryption,
        service = service, remoteFolder = "fixture-folder", secrets = listOf(dummy, dummy, dummy),
        endpoint = "https://s3.example.com", bucket = "fixture-bucket",
        advanced = if (service == "template") """{"template":{"put":{"url":"https://example.com/{name}"},"get":{"url":"https://example.com/{name}"},"delete":{"url":"https://example.com/{name}"},"list":{"url":"https://example.com/list","namesRegex":"(.*)"}}}""" else "{}",
    )

    @Test fun buildsLocalConfigurationsAndPreservesTheirOutboundDuringVpnPreparation() {
        val source = form("local").copy(remoteFolder = "/tmp/xdrive-fixture").build()
        assertNull(form("local").issue())
        val runtime = Json.parseToJsonElement(JsonVpnConfig.prepare(source.toString(), JsonVpnTransport())).jsonObject
        assertEquals(source["outbounds"]!!.jsonArray.first(), runtime["outbounds"]!!.jsonArray.first())
        val outbound = source["outbounds"]!!.jsonArray.first().jsonObject
        assertTrue(outbound["mux"]!!.jsonObject["enabled"]!!.jsonPrimitive.boolean)
        assertEquals("xdrive", outbound["streamSettings"]!!.jsonObject["network"]!!.jsonPrimitive.content)
        assertNull(outbound["streamSettings"]!!.jsonObject["xdriveSettings"]!!.jsonObject["secrets"])
        assertNull(source["inbounds"])
    }

    @Test fun everyBackendRoundTripsWithoutCreatingAShareLink() {
        for (service in listOf("google-drive", "s3", "template", "local")) {
            val profile = form(service)
            assertNull(profile.issue(), service)
            val config = profile.build()
            val reopened = assertNotNull(XDriveForm.fromJson(config.toString()), service)
            assertEquals(config, reopened.build(), service)
        }
    }

    @Test fun validatesEncryptionFlowStorageAndNumericFieldsWithoutEchoingSecrets() {
        assertEquals("encryption", form().copy(encryption = "none").issue())
        assertEquals("flow", form().copy(flow = "xtls-rprx-vision").issue())
        assertEquals("security", form().copy(security = "reality").issue())
        assertEquals("secrets", form().copy(secrets = listOf(dummy)).issue())
        assertEquals("remoteFolder", form().copy(remoteFolder = "https://drive.google.com/folders/id").issue())
        assertEquals("s3.endpoint", form("s3").copy(endpoint = "https://user:secret@example.com/").issue())
        assertEquals("remoteFolder", form("s3").copy(remoteFolder = "../folder").issue())
        assertEquals("segmentBytes", form().copy(advanced = """{"segmentBytes":4294967296}""").issue())
        assertEquals("segmentBytes", form().copy(advanced = """{"segmentBytes":"12"}""").issue())
        assertEquals("advanced", form().copy(advanced = "{ secret-broken-json").issue())
        assertEquals("advanced", form().copy(advanced = """{"secrets":["$dummy"]}""").issue())
        assertEquals("xmux", form().copy(advanced = """{"xmux":{"maxConnections":2,"maxConcurrency":4}}""").issue())
        for (value in listOf("true", "[]", "\"garbage\"", "2147483648")) {
            assertEquals("xmux.maxConnections", form().copy(advanced = """{"xmux":{"maxConnections":$value}}""").issue())
        }
        assertEquals("xmux.hKeepAlivePeriod", form().copy(advanced = """{"xmux":{"hKeepAlivePeriod":[]}}""").issue())
        assertNull(form().copy(advanced = """{"xmux":{"maxConnections":"1-4","cMaxReuseTimes":-1,"hKeepAlivePeriod":-1}}""").issue())
        assertNull(form().copy(advanced = """{"segmentBytes":4294967295}""").issue())
    }

    @Test fun editsKeepUnknownFieldsAcrossEveryNestedLayerAndKeepCredentialOrder() {
        val original = form("s3").copy(secrets = listOf("key+&%", "secret, ü", "token"), advanced = """{"future":{"keep":42}}""").build()
        val root = original.toMutableMap().apply { put("custom", JsonPrimitive(42)) }
        val reopened = assertNotNull(XDriveForm.fromJson(JsonObject(root).toString()))
        assertEquals(JsonObject(root), reopened.build())
        val edited = reopened.copy(id = "renamed-user", remoteFolder = "new-prefix").build()
        assertEquals(JsonPrimitive(42), edited["custom"])
        val settings = edited["outbounds"]!!.jsonArray[0].jsonObject["streamSettings"]!!.jsonObject["xdriveSettings"]!!.jsonObject
        assertEquals(listOf("key+&%", "secret, ü", "token"), settings["secrets"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals(42, settings["future"]!!.jsonObject["keep"]!!.jsonPrimitive.int)
    }

    @Test fun rejectsUnsupportedImportShapesAndDoesNotRepairInvalidImportedMux() {
        assertNull(XDriveForm.fromJson("not-json"))
        val config = form().build()
        val outbound = config["outbounds"]!!.jsonArray[0].jsonObject
        val invalid = JsonObject(outbound + ("mux" to buildJsonObject { put("enabled", false) }))
        val imported = assertNotNull(XDriveForm.fromJson(JsonObject(config + ("outbounds" to JsonArray(listOf(invalid)))).toString()))
        assertEquals("mux", imported.issue())
        val multiple = JsonObject(config + ("outbounds" to JsonArray(listOf(outbound, outbound))))
        assertNull(XDriveForm.fromJson(multiple.toString()))
    }

    @Test fun unsupportedDormantOrMalformedSubtreesStayInRawEditor() {
        val config = form("s3").build()
        val outbound = config["outbounds"]!!.jsonArray[0].jsonObject
        val stream = outbound["streamSettings"]!!.jsonObject
        fun modified(streamValue: JsonObject) = JsonObject(config + ("outbounds" to JsonArray(listOf(
            JsonObject(outbound + ("streamSettings" to streamValue)),
        )))).toString()
        assertNull(XDriveForm.fromJson(modified(JsonObject(stream + ("tlsSettings" to buildJsonObject { put("future", 42) })))))
        val settings = stream["xdriveSettings"]!!.jsonObject
        assertNull(XDriveForm.fromJson(modified(JsonObject(stream + ("xdriveSettings" to JsonObject(settings +
            ("s3" to JsonObject(settings["s3"]!!.jsonObject + ("pathStyle" to JsonPrimitive("false"))))))))))
        assertNull(XDriveForm.fromJson(modified(JsonObject(stream + ("xdriveSettings" to JsonObject(settings +
            ("service" to JsonPrimitive("google-drive"))))))))
    }

    @Test fun unchangedTlsFieldsAndUnknownNestedFieldsSurviveStructuredEdits() {
        for (alpn in listOf(JsonArray(emptyList()), JsonArray(listOf(JsonPrimitive("custom,protocol"))))) {
            val source = form("s3").copy(security = "tls").build()
            val out = source["outbounds"]!!.jsonArray[0].jsonObject
            fun JsonObject.extra() = JsonObject(this + ("future" to JsonPrimitive(42)))
            val settings = out["settings"]!!.jsonObject
            val server = settings["vnext"]!!.jsonArray[0].jsonObject
            val user = server["users"]!!.jsonArray[0].jsonObject.extra()
            val newServer = JsonObject(server.extra() + ("users" to JsonArray(listOf(user))))
            val stream = out["streamSettings"]!!.jsonObject
            val drive = stream["xdriveSettings"]!!.jsonObject
            val newDrive = JsonObject(drive + ("s3" to drive["s3"]!!.jsonObject.extra()))
            val tls = buildJsonObject { put("serverName", ""); put("alpn", alpn); put("future", 42) }
            val newStream = JsonObject(stream.extra() + mapOf("xdriveSettings" to newDrive, "tlsSettings" to tls))
            val newOut = JsonObject(out.extra() + mapOf("settings" to JsonObject(settings + ("vnext" to JsonArray(listOf(newServer)))), "streamSettings" to newStream))
            val root = JsonObject(source + ("outbounds" to JsonArray(listOf(newOut))))
            val imported = assertNotNull(XDriveForm.fromJson(root.toString()))
            assertEquals(root, imported.build())
            val changed = imported.copy(id = "updated").build()["outbounds"]!!.jsonArray[0].jsonObject
            assertEquals(newStream, changed["streamSettings"])
            assertEquals(JsonPrimitive(42), changed["settings"]!!.jsonObject["vnext"]!!.jsonArray[0].jsonObject["future"])
        }
    }

    @Test fun importedInvalidFlowAndTestpreRemainVisibleValidationErrors() {
        val config = form().build()
        val out = config["outbounds"]!!.jsonArray[0].jsonObject
        val settings = out["settings"]!!.jsonObject
        val server = settings["vnext"]!!.jsonArray[0].jsonObject
        val user = server["users"]!!.jsonArray[0].jsonObject
        for ((field, value) in listOf("flow" to JsonPrimitive("xtls-rprx-vision"), "testpre" to JsonPrimitive(1))) {
            val newUser = JsonObject(user + (field to value))
            val newServer = JsonObject(server + ("users" to JsonArray(listOf(newUser))))
            val newOut = JsonObject(out + ("settings" to JsonObject(settings + ("vnext" to JsonArray(listOf(newServer))))))
            val root = JsonObject(config + ("outbounds" to JsonArray(listOf(newOut))))
            assertEquals(field, assertNotNull(XDriveForm.fromJson(root.toString())).issue())
        }
    }

    @Test fun jsonRecordsCannotBeSharedEvenWithAnOrdinaryLookingUrl() {
        val node = Node(protocolPrefix = JSON_CONFIG_PREFIX, address = "", port = 0, subscriptionId = -1,
            url = "vless://pretend", jsonData = form().build().toString())
        assertFalse(node.canShareConfiguration)
        assertFalse(node.copy(jsonData = null).canShareConfiguration)
        assertTrue(node.copy(protocolPrefix = "vless", jsonData = null).canShareConfiguration)
    }
}
