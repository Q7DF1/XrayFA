package com.android.xrayfa.datastore

/** A user-selected map origin, independent of IP geolocation or device location services. */
data class ConnectMapOrigin(val latitude: Double, val longitude: Double) {
    init {
        require(latitude.isFinite() && latitude in -90.0..90.0)
        require(longitude.isFinite() && longitude in -180.0..180.0)
    }

    companion object {
        fun fromCoordinates(latitude: Double?, longitude: Double?): ConnectMapOrigin? {
            if (latitude == null || longitude == null || !latitude.isFinite() || !longitude.isFinite() ||
                latitude !in -90.0..90.0 || longitude !in -180.0..180.0
            ) return null
            return ConnectMapOrigin(latitude, longitude)
        }
    }
}
