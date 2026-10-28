package com.android.xrayfa.common.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MmdbCityLookupTest {
    @Test
    fun coordinatesAndLocalizedNamesAreDecodedForIpv4AndIpv6() {
        val reader = MmdbCityLookup(cityDatabase())
        for (ip in listOf("81.2.69.160", "2001:218::")) {
            val location = assertNotNull(reader.location(ip, "zh-CN"))
            assertEquals("伦敦", location.city)
            assertEquals("England", location.region)
            assertEquals("United Kingdom", location.country)
            assertEquals(51.5142, location.latitude)
            assertEquals(-0.0931, location.longitude)
            assertEquals("London", reader.location(ip, "ko")?.city)
        }
    }

    @Test
    fun absentOrInvalidCoordinatesNeverProduceAPin() {
        assertNull(MmdbCityLookup(cityDatabase(latitude = 91.0)).location("1.1.1.1"))
        assertNull(MmdbCityLookup(cityDatabase(latitude = Double.NaN)).location("1.1.1.1"))
        assertNull(MmdbCityLookup(cityDatabase(withLocation = false)).location("1.1.1.1"))
        assertNull(MmdbCityLookup(cityDatabase(empty = true)).location("1.1.1.1"))
        assertNull(MmdbCityLookup(cityDatabase()).location("example.org"))
    }

    @Test
    fun wrongDatabaseAndCorruptFilesAreRejected() {
        assertFailsWith<IllegalArgumentException> { MmdbCityLookup(cityDatabase(type = "GeoLite2-Country")) }
        assertFailsWith<Exception> { MmdbCityLookup(byteArrayOf(1, 2, 3)) }
        assertFailsWith<Exception> { MmdbCityLookup(cityDatabase().dropLast(20).toByteArray()) }
    }

    private fun cityDatabase(
        latitude: Double = 51.5142,
        withLocation: Boolean = true,
        empty: Boolean = false,
        type: String = "GeoIP2-City",
    ): ByteArray {
        val names = { value: String -> mapOf("names" to mapOf("en" to value)) }
        val record = linkedMapOf<String, Any>(
            "city" to mapOf("names" to mapOf("en" to "London", "zh-CN" to "伦敦")),
            "country" to names("United Kingdom"),
            "subdivisions" to listOf(names("England")),
        )
        if (withLocation) record["location"] = mapOf("latitude" to latitude, "longitude" to -0.0931)
        // One 24-bit node: either branch points to the first data record, or the empty sentinel.
        val pointer = if (empty) 1 else 17
        val tree = byteArrayOf(0, 0, pointer.toByte(), 0, 0, pointer.toByte())
        val marker = byteArrayOf(0xab.toByte(), 0xcd.toByte(), 0xef.toByte()) + "MaxMind.com".encodeToByteArray()
        return tree + ByteArray(16) + encode(record) + marker + encode(mapOf(
            "node_count" to 1, "record_size" to 24, "ip_version" to 6, "database_type" to type,
        ))
    }

    private fun encode(value: Any): ByteArray = when (value) {
        is String -> value.encodeToByteArray().let { byteArrayOf((0x40 or it.size).toByte()) + it }
        is Int -> byteArrayOf(0xc1.toByte(), value.toByte())
        is Double -> byteArrayOf(0x68) + ByteArray(8) { (value.toBits() ushr (56 - it * 8)).toByte() }
        is Map<*, *> -> byteArrayOf((0xe0 or value.size).toByte()) +
            value.entries.flatMap { (key, item) -> (encode(key!!) + encode(item!!)).toList() }.toByteArray()
        is List<*> -> byteArrayOf(value.size.toByte(), 4) + value.flatMap { encode(it!!).toList() }.toByteArray()
        else -> error("Unsupported test value")
    }
}
