package com.android.xrayfa.cli

import com.android.xrayfa.config.XDriveForm
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.*

class JsonImportTest {
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
        assertEquals(JsonPrimitive(10999), config["inbounds"]!!.jsonArray.single().jsonObject["port"])
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
