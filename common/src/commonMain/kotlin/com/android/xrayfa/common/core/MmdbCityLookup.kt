package com.android.xrayfa.common.core

/** A validated reader is retained so repeated lookups do not reparse the MMDB metadata. */
class MmdbCityLookup(database: ByteArray) {
    private val reader = MmdbReader(database)

    init {
        require(reader.databaseType.contains("City", ignoreCase = true)) { "A City MMDB is required" }
    }

    enum class MissReason { INVALID_IP, NO_RECORD, NO_LOCATION, NO_LATITUDE, NO_LONGITUDE, INVALID_COORDINATES }

    fun location(
        ip: String,
        language: String = "en",
        onMiss: (MissReason) -> Unit = {},
    ): CityLocation? {
        fun missing(reason: MissReason): CityLocation? {
            onMiss(reason)
            return null
        }
        val bytes = parseIpLiteral(ip) ?: return missing(MissReason.INVALID_IP)
        val record = reader.record(bytes)?.v ?: return missing(MissReason.NO_RECORD)
        val location = (record["location"] as? Val.Map)?.v ?: return missing(MissReason.NO_LOCATION)
        val latitude = (location["latitude"] as? Val.Decimal)?.v ?: return missing(MissReason.NO_LATITUDE)
        val longitude = (location["longitude"] as? Val.Decimal)?.v ?: return missing(MissReason.NO_LONGITUDE)
        if (!latitude.isFinite() || !longitude.isFinite() ||
            latitude !in -90.0..90.0 || longitude !in -180.0..180.0
        ) return missing(MissReason.INVALID_COORDINATES)
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
