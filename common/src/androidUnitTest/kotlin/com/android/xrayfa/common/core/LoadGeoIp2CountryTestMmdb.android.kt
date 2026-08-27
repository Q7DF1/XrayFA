package com.android.xrayfa.common.core

internal actual fun loadGeoIp2CountryTestMmdb(): ByteArray? {
    return object {}
        .javaClass
        .classLoader
        ?.getResourceAsStream("GeoIP2-Country-Test.mmdb")
        ?.readBytes()
}
