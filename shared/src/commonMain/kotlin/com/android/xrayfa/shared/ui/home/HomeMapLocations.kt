package com.android.xrayfa.shared.ui.home

/** Approximate IP geolocation returned by the configured IP lookup provider. */
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
)
