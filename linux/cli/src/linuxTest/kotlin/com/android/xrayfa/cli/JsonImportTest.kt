package com.android.xrayfa.cli

import com.android.xrayfa.config.XDriveForm
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class JsonImportTest {
    @Test
    fun tunRejectsImportedPoliciesAndChainsRatherThanDiscardingThem() = runBlocking {
        val parser = NodeParser()
        for (text in listOf(
            """{"outbounds":[{"protocol":"freedom"},{"protocol":"blackhole"}]}""",
            """{"outbounds":[{"protocol":"freedom"}],"routing":{}}""",
            """{"outbounds":[{"protocol":"freedom"}],"dns":{"servers":["192.0.2.53"]}}""",
            """{"outbounds":[{"protocol":"freedom"}],"policy":{}}""",
        )) {
            val record = nodeRecord(parser.parse(text), 1)
            val outbound = parser.outbound(text)
            assertEquals(ExitCode.CONFIGURATION,
                assertFailsWith<CliError> { nodeTunOutbound(record, outbound) }.exitCode)
            // Explicit SOCKS composition still retains the user's complete JSON policy.
            val config = nodeSocksConfig(record, outbound, LogLevel.INFO)
            val original = json.parseToJsonElement(text).jsonObject
            for (key in listOf("outbounds", "routing", "dns", "policy"))
                assertEquals(original[key], config[key])
        }
        val single = """{"outbounds":[{"protocol":"freedom","mux":{"enabled":true,"concurrency":8}}]}"""
        val record = nodeRecord(parser.parse(single), 1)
        val outbound = parser.outbound(single)
        assertEquals(outbound, nodeTunOutbound(record, outbound))
    }

    @Test
    fun routeCountdownIsIgnoredButRoutingChangesRemainSignificant() {
        fun route(seconds: Int, gateway: String = "fe80::2", metric: Int = 100) = buildJsonObject {
            put("raw", "default via $gateway dev eth0 proto ra metric $metric expires ${seconds}sec pref medium")
            put("gateway", gateway)
            put("metric", metric)
            put("expires", seconds)
        }
        assertEquals(normalizedNetwork(route(1800)), normalizedNetwork(route(1799)))
        assertNotEquals(normalizedNetwork(route(1800)), normalizedNetwork(route(1799, gateway = "fe80::3")))
        assertNotEquals(normalizedNetwork(route(1800)), normalizedNetwork(route(1799, metric = 101)))
    }

    @Test
    fun tunAlsoExposesLoopbackSocksAndPinsUnderlay() {
        val outbound = json.parseToJsonElement("""{"protocol":"vless","settings":{}}""").jsonObject
        val plan = NetworkPlan("eth0", "xfa_1_2", JsonObject(emptyMap()), emptyList())
        val config = runtimeFinalConfig(outbound, LogLevel.INFO, plan)
        val inbounds = config.getValue("inbounds").jsonArray.map { it.jsonObject }
        assertEquals(2, inbounds.size)
        val tun = inbounds.single { it["protocol"] == JsonPrimitive("tun") }
        assertEquals(JsonPrimitive("eth0"), tun.getValue("settings").jsonObject["autoOutboundsInterface"])
        val socks = inbounds.single { it["protocol"] == JsonPrimitive("socks") }
        assertEquals(JsonPrimitive("127.0.0.1"), socks["listen"])
        assertEquals(JsonPrimitive(10808), socks["port"])
        assertEquals(JsonPrimitive(true), socks.getValue("settings").jsonObject["udp"])
    }

    @Test
    fun xdriveUsesSharedValidationAndPreservesStorage() = runBlocking {
        val source = XDriveForm(id = "00000000-0000-4000-8000-000000000001",
            address = "127.0.0.1", port = 443, encryption = "mlkem768x25519plus.native.1rtt.${"A".repeat(43)}", security = "none",
            service = "local", remoteFolder = "/tmp/synthetic-xdrive").build().toString()
        val parser = NodeParser()
        val node = parser.parse(source)
        val record = nodeRecord(node, 1)
        val outbound = parser.outbound(source)
        assertEquals("json-config", node.protocolPrefix)
        assertEquals(record, ValidatedNode.from(record, outbound).record)
        val config = nodeSocksConfig(record, outbound, LogLevel.INFO)
        assertEquals(json.parseToJsonElement(source).jsonObject["outbounds"], config["outbounds"])
        assertEquals(JsonPrimitive(10808), config["inbounds"]!!.jsonArray.single().jsonObject["port"])
        val secrets = secretValues(config)
        assertTrue("/tmp/synthetic-xdrive" in secrets)
        assertFalse(redact("/tmp/synthetic-xdrive", secrets).contains("synthetic-xdrive"))
    }

    @Test
    fun malformedAmbiguousAndExternalInputsAreRejected() {
        for (input in listOf(
            """{"outbounds":[{"protocol":"freedom"}],"outbounds":[]}""",
            """{"outbounds":[{"protocol":"freedom"}],"api":{}}""",
            """{"outbounds":[{"protocol":"freedom"}],"routing":{"rules":[{"inboundTag":["old"]}]}}""",
            """{"outbounds":[{"protocol":"vless","streamSettings":{"network":"xdrive","xdriveSettings":{}}}]}""",
            """{"outbounds":[{"protocol":"freedom"}],"log":{"error":"/tmp/operator.log"}}""",
        )) assertFails { inspectCliJson(input) }
    }
}
