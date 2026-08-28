package com.android.xrayfa.common.core

/** MaxMind GeoIP2 Country test MMDB; JVM loads from androidUnitTest resources. */
internal expect fun loadGeoIp2CountryTestMmdb(): ByteArray?
