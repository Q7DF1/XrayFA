@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.android.xrayfa.cli

import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue
import platform.posix.chmod
import platform.posix.mkdtemp
import platform.posix.rmdir
import platform.posix.unlink

class NetworkInventoryTest {
    @Test
    fun jsonHelperPreservesLargeSingleLineInterfaceInventory() = memScoped {
        val root = mkdtemp("/tmp/xrayfa-inventory-XXXXXX".cstr.ptr)?.toKString()
            ?: error("mkdtemp failed")
        val path = "$root/ip-fixture"
        try {
            val expected = buildJsonArray {
                repeat(256) { index ->
                    add(buildJsonObject {
                        put("ifindex", index + 1)
                        put("ifname", "cvd-tap-$index")
                        put("mtu", 1500)
                        put("ifalias", "x".repeat(350))
                        putJsonArray("addr_info") {}
                    })
                }
            }
            val document = expected.toString()
            assertTrue(document.length > 65536)
            newFile(path, "#!/bin/sh\nprintf '%s\\n' '$document'\n")
            assertEquals(0, chmod(path, 448u))
            // Reproduce the old unlogged helper's clipped JSON capture.
            val clipped = tool(path, listOf("-j", "address", "show"))
            assertEquals(0, clipped.exit)
            assertFails { json.parseToJsonElement(clipped.output) }
            assertEquals(expected, systemJson(path, "address", "show"))
        } finally {
            unlink(path)
            rmdir(root)
        }
    }

    @Test
    fun tunInspectionUsesCompleteHostInterfaceInventory() {
        val name = TunPolicy.CURRENT.interfaceName
        val address = ipJson("address", "show").map { it.jsonObject }
            .firstOrNull { it["ifname"] == JsonPrimitive(name) }
        val expected = address?.let {
            JsonObject(it.filterKeys { key -> key in setOf("ifindex", "ifname", "mtu", "addr_info") })
        }
        assertEquals(expected, tunIdentity())
        val link = ipJson("link", "show").map { it.jsonObject }
            .firstOrNull { it["ifname"] == JsonPrimitive(name) }
        assertEquals(link, tunLinkIdentity())
    }
}
