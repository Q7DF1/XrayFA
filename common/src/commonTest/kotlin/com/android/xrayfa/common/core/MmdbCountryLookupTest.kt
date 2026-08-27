package com.android.xrayfa.common.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MmdbCountryLookupTest {

    @Test
    fun knownIpv4_returnsIsoCode() {
        val db = testMmdb()
        assertEquals("GB", MmdbCountryLookup.isoCode(db, "81.2.69.160"))
        assertEquals("US", MmdbCountryLookup.isoCode(db, "74.209.24.0"))
    }

    @Test
    fun knownIpv6_returnsIsoCode() {
        assertEquals("JP", MmdbCountryLookup.isoCode(testMmdb(), "2001:218::"))
    }

    @Test
    fun unknownOrInvalidIp_returnsNull() {
        val db = testMmdb()
        assertNull(MmdbCountryLookup.isoCode(db, "10.0.0.1"))
        assertNull(MmdbCountryLookup.isoCode(db, "not-an-ip"))
        assertNull(MmdbCountryLookup.isoCode(ByteArray(0), "8.8.8.8"))
    }

    @Test
    fun lookupFeedsDisplayFlag() {
        assertEquals(
            "🇬🇧",
            MmdbCountryLookup.countryFlag(testMmdb(), "81.2.69.160"),
        )
    }

    /** MaxMind-DB Apache-2.0 test fixture (`GeoIP2-Country-Test.mmdb`). */
    private fun testMmdb(): ByteArray {
        val stream =
            MmdbCountryLookupTest::class.java.classLoader!!.getResourceAsStream(
                "GeoIP2-Country-Test.mmdb",
            ) ?: error("GeoIP2-Country-Test.mmdb missing from test resources")
        return stream.readBytes()
    }
}
