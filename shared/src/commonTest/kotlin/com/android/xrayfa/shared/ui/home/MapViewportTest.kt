package com.android.xrayfa.shared.ui.home

import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MapViewportTest {
    @Test
    fun dateLineRoutesPreserveEndpointsAndTangentAcrossBothEdges() {
        val viewport = MapViewport.fit(600f, 300f)
        for ((start, end) in listOf(170f to -170f, -170f to 170f, 180f to -175f, -180f to 175f)) {
            val from = viewport.project(start, 35f)
            val to = viewport.project(end, -20f)
            val curves = makeMapRoute(from, to, viewport)
            assertEquals(2, curves.size)
            val first = curves.first()
            val second = curves.last()
            assertEquals(from, first.start)
            assertEquals(to, second.end)
            assertEquals(first.end.y, second.start.y, 0.001f)
            val incoming = first.end - first.control
            val outgoing = second.control - second.start
            assertEquals(0f, incoming.x * outgoing.y - incoming.y * outgoing.x, 0.01f)
            for (curve in curves) {
                for (point in listOf(curve.start, curve.control, curve.end)) {
                    assertTrue(point.x.isFinite() && point.y.isFinite())
                    assertTrue(point.x in viewport.left - 0.001f..viewport.left + viewport.width + 0.001f)
                    assertTrue(point.y in viewport.top..viewport.top + viewport.height)
                }
            }
        }
    }

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

    @Test
    fun croppedOverviewKeepsPinsAndSelectedCoordinatesAligned() {
        val viewport = MapViewport.fit(600f, 220f, north = 85f, south = -55f)
        for ((latitude, longitude) in listOf(31.23 to 121.47, 50.11 to 8.68, -55.0 to -180.0, 85.0 to 180.0)) {
            val point = viewport.project(longitude.toFloat(), latitude.toFloat())
            val coordinate = assertNotNull(viewport.unproject(point))
            assertEquals(latitude, coordinate.latitude, 0.0001)
            assertEquals(longitude, coordinate.longitude, 0.0001)
        }
    }
}
