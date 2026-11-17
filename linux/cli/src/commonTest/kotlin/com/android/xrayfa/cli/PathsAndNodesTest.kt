package com.android.xrayfa.cli

import kotlin.test.*
import kotlinx.serialization.json.*

class PathsAndNodesTest {
    @Test
    fun defaultsAndCallerIdentity() {
        val paths = CliPaths.resolve("/home/alice", 1000u, null, true)
        assertEquals("/home/alice/.local/share/xrayfa", paths.dataDir)
        assertEquals("/home/alice/.local/state/xrayfa/logs", paths.logDir)
        assertEquals("/var/log/xrayfa/1000", paths.systemLogDir)
        assertEquals("/home/alice/.local/share/xrayfa/.local/share/xrayfa", paths.legacyDataDir)
        // Effective UID is deliberately not a path input.
        assertEquals(paths, CliPaths.resolve("/home/alice", 1000u, null, true))
    }

    @Test
    fun isolatedAndFallbackLayouts() {
        val isolated = CliPaths.resolve("/home/alice", 1000u, "/tmp/test", false)
        assertEquals("/tmp/test/.local/share/xrayfa", isolated.dataDir)
        assertNull(isolated.legacyDataDir)
        val fallback = CliPaths.resolve("/home/alice", 1000u, null, false)
        assertEquals("/home/alice/Xray/.xrayfa/.local/share/xrayfa", fallback.dataDir)
        assertNull(fallback.legacyDataDir)
        assertFailsWith<IllegalArgumentException> { CliPaths.resolve("relative", 1u, null, true) }
    }

    @Test
    fun legacyStoreIsPreservedAndAmbiguityFails() {
        val paths = CliPaths.resolve("/home/alice", 1000u, null, true)
        assertEquals(paths.dataDir, paths.storeDirectory { StorePresence.MISSING })
        assertEquals(
            paths.legacyDataDir,
            paths.storeDirectory {
                if (it == paths.legacyDataDir) StorePresence.PRESENT else StorePresence.MISSING
            },
        )
        assertFailsWith<IllegalArgumentException> { paths.storeDirectory { StorePresence.PRESENT } }
        for (unsafe in listOf(paths.dataDir, paths.legacyDataDir)) {
            assertFailsWith<IllegalArgumentException> {
                paths.storeDirectory {
                    if (it == unsafe) StorePresence.UNSAFE else StorePresence.MISSING
                }
            }
        }
    }

    @Test
    fun coreSelectionHasOnePrecedenceForAllCommands() {
        val home = "/home/alice"
        val app = "$home/app/Xray/xray"
        val old = "$home/Xray/xray"
        for ((existing, expected) in
            listOf(
                emptySet<String>() to app,
                setOf(app) to app,
                setOf(old) to old,
                setOf(app, old) to app,
            )) {
            assertEquals(expected, CoreLocator.locate(home, null, existing::contains))
            assertEquals(
                "/custom/core",
                CoreLocator.locate(home, "/custom/core", existing::contains),
            )
        }
    }

    private val record =
        Json.parseToJsonElement(
                """{"id":1,"url":"socks://192.0.2.1:1080","protocol":"socks5","address":"192.0.2.1","port":1080,"name":"fixture"}"""
            )
            .jsonObject

    private fun store(nodes: List<JsonObject> = listOf(record), next: Int = 2) = buildJsonObject {
        put("schemaVersion", 1)
        put("nextId", next)
        put("nodes", JsonArray(nodes))
    }

    private val outbound =
        Json.parseToJsonElement(
                """{"protocol":"socks","settings":{"servers":[{"address":"192.0.2.1","port":1080}]}}"""
            )
            .jsonObject

    @Test
    fun oldNodeSchemaRoundTripsAndFindsMissingId() {
        val original = store()
        assertEquals(original, NodeStoreCodec.decode(original.toString()))
        assertEquals(record, NodeStoreCodec.find(original, 1))
        assertNull(NodeStoreCodec.find(original, 2))
        assertEquals(
            NodeStoreCodec.empty(),
            NodeStoreCodec.decode("""{"schemaVersion":1,"nextId":1,"nodes":[]}"""),
        )
    }

    @Test
    fun corruptStoresAreRejectedBySharedCodec() {
        val invalid =
            listOf(
                store(listOf(record, record)),
                store(next = 1),
                store(next = 0),
                store(listOf(JsonObject(record + ("protocol" to JsonPrimitive("unknown"))))),
                store(listOf(JsonObject(record + ("port" to JsonPrimitive(0))))),
                JsonObject(store() + ("schemaVersion" to JsonPrimitive(2))),
            )
        invalid.forEach { assertFails { NodeStoreCodec.decode(it.toString()) } }
        assertFails { NodeStoreCodec.decode("broken JSON") }
    }

    @Test
    fun parsedEndpointAndProtocolMustMatchStoredSnapshot() {
        assertEquals(outbound, ValidatedNode.from(record, outbound).outbound)
        for ((field, value) in
            listOf(
                "address" to JsonPrimitive("192.0.2.2"),
                "port" to JsonPrimitive(1081),
                "protocol" to JsonPrimitive("http"),
            )) {
            assertFails { ValidatedNode.from(JsonObject(record + (field to value)), outbound) }
        }
    }

    @Test
    fun protocolAliasesAndEndpointLayoutsStayConsistent() {
        for ((stored, parsed) in
            listOf(
                "ss" to "shadowsocks",
                "hy2" to "hysteria",
                "hysteria2" to "hysteria",
                "socks5" to "socks",
                "vless" to "vless",
                "vmess" to "vmess",
                "trojan" to "trojan",
                "http" to "http",
            )) {
            val endpoint = buildJsonObject {
                put("address", "192.0.2.1")
                put("port", 1080)
            }
            val settings =
                when (parsed) {
                    "vless",
                    "vmess" -> buildJsonObject { put("vnext", JsonArray(listOf(endpoint))) }
                    "hysteria" -> endpoint
                    else -> buildJsonObject { put("servers", JsonArray(listOf(endpoint))) }
                }
            val output = buildJsonObject {
                put("protocol", parsed)
                put("settings", settings)
            }
            val input = JsonObject(record + ("protocol" to JsonPrimitive(stored)))
            assertEquals(output, ValidatedNode.from(input, output).outbound)
        }
    }
}
