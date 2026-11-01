package com.android.xrayfa.shared.ui.home

/** A manually selected origin or an approximate node location from the local City database. */
data class HomeMapLocation(
    val ipAddress: String,
    val city: String?,
    val region: String?,
    val country: String?,
    val latitude: Double,
    val longitude: Double,
) {
    val displayName: String
        get() = listOfNotNull(city, region, country).distinct().joinToString(", ")
}

data class HomeMapLocations(
    val user: HomeMapLocation? = null,
    val server: HomeMapLocation? = null,
    val issue: HomeMapIssue? = null,
)

enum class HomeMapIssue {
    DATABASE_MISSING,
    DATABASE_INVALID,
    ORIGIN_MISSING,
    SERVER_UNAVAILABLE,
    DNS_UNAVAILABLE,
    EXIT_UNAVAILABLE,
    EXIT_TIMEOUT,
    EXIT_SERVICE_ERROR,
    EXIT_RATE_LIMITED,
}
