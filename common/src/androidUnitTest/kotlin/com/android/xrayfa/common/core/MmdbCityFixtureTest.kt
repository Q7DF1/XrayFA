package com.android.xrayfa.common.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MmdbCityFixtureTest {
    // MaxMind's official fixture: https://github.com/maxmind/MaxMind-DB/tree/main/test-data
    @Test
    fun readsOfficialCityFixtureIncludingCompressedPointers() {
        val database = checkNotNull(javaClass.classLoader?.getResourceAsStream("GeoIP2-City-Test.mmdb"))
            .use { it.readBytes() }
        val reader = MmdbCityLookup(database)
        val london = assertNotNull(reader.location("81.2.69.160"))
        assertEquals("London", london.city)
        assertEquals(51.5142, london.latitude)
        assertEquals(-0.0931, london.longitude)
        assertNotNull(reader.location("2001:218::"))
        assertNull(reader.location("10.0.0.1"))
    }
}
