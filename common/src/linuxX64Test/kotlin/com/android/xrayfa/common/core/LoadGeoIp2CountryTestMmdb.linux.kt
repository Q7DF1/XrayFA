package com.android.xrayfa.common.core

internal actual fun loadGeoIp2CountryTestMmdb(): ByteArray? =
    AssetFiles.read("src/androidUnitTest/resources/GeoIP2-Country-Test.mmdb")
