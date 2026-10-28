package com.android.xrayfa.shared.ui.home

import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MapViewportTest {
    @Test
    fun tappedLocationMatchesThePinOnWideAndTallMaps() {
        for ((width, height) in listOf(600f to 200f, 300f to 300f)) {
            val viewport = MapViewport.fit(width, height)
            for ((latitude, longitude) in listOf(31.23 to 121.47, -33.87 to 151.21, 0.0 to 0.0, 90.0 to -180.0)) {
                val tap = viewport.project(longitude.toFloat(), latitude.toFloat())
                val origin = assertNotNull(viewport.unproject(tap))
                assertEquals(latitude, origin.latitude, 0.0001)
                assertEquals(longitude, origin.longitude, 0.0001)
            }
        }
    }

    @Test
    fun mapMarginsAndInvalidTouchesDoNotSelectALocation() {
        assertNull(MapViewport.fit(600f, 200f).unproject(Offset(0f, 100f)))
        assertNull(MapViewport.fit(300f, 300f).unproject(Offset(150f, 0f)))
        assertNull(MapViewport.fit(300f, 300f).unproject(Offset(Float.NaN, 100f)))
        assertNull(MapViewport.fit(0f, 0f).unproject(Offset.Zero))
    }
}
