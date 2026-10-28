package com.android.xrayfa.common.core

/** A validated reader is retained so repeated lookups do not reparse the MMDB metadata. */
class MmdbCityLookup(database: ByteArray) {
    private val reader = MmdbReader(database)

    init {
        require(reader.databaseType.contains("City", ignoreCase = true)) { "A City MMDB is required" }
    }

    fun location(ip: String, language: String = "en"): CityLocation? {
        val bytes = parseIpLiteral(ip) ?: return null
        val record = reader.record(bytes)?.v ?: return null
        val location = (record["location"] as? Val.Map)?.v ?: return null
        val latitude = (location["latitude"] as? Val.Decimal)?.v ?: return null
        val longitude = (location["longitude"] as? Val.Decimal)?.v ?: return null
        if (!latitude.isFinite() || !longitude.isFinite() ||
            latitude !in -90.0..90.0 || longitude !in -180.0..180.0
        ) return null
        fun name(value: Val?): String? {
            val names = ((value as? Val.Map)?.v?.get("names") as? Val.Map)?.v ?: return null
            return ((names[language] ?: names["en"]) as? Val.Str)?.v?.takeIf { it.isNotBlank() }
        }
        return CityLocation(
            city = name(record["city"]),
            region = name((record["subdivisions"] as? Val.Arr)?.v?.firstOrNull()),
            country = name(record["country"]),
            latitude = latitude,
            longitude = longitude,
        )
    }
}

data class CityLocation(
    val city: String?,
    val region: String?,
    val country: String?,
    val latitude: Double,
    val longitude: Double,
)
