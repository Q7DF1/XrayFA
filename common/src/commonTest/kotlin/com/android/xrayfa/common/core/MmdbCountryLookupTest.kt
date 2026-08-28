package com.android.xrayfa.common.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MmdbCountryLookupTest {

    @Test
    fun knownIpv4_returnsIsoCode() {
        val db = loadGeoIp2CountryTestMmdb() ?: return
        assertEquals("GB", MmdbCountryLookup.isoCode(db, "81.2.69.160"))
        assertEquals("US", MmdbCountryLookup.isoCode(db, "74.209.24.0"))
    }

    @Test
    fun knownIpv6_returnsIsoCode() {
        val db = loadGeoIp2CountryTestMmdb() ?: return
        assertEquals("JP", MmdbCountryLookup.isoCode(db, "2001:218::"))
    }

    @Test
    fun unknownOrInvalidIp_returnsNull() {
        val db = loadGeoIp2CountryTestMmdb() ?: return
        assertNull(MmdbCountryLookup.isoCode(db, "10.0.0.1"))
        assertNull(MmdbCountryLookup.isoCode(db, "not-an-ip"))
        assertNull(MmdbCountryLookup.isoCode(ByteArray(0), "8.8.8.8"))
    }

    @Test
    fun lookupFeedsDisplayFlag() {
        val db = loadGeoIp2CountryTestMmdb() ?: return
        assertEquals(
            "🇬🇧",
            MmdbCountryLookup.countryFlag(db, "81.2.69.160"),
        )
    }
}
