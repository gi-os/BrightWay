package com.gios.brightway

import com.gios.brightway.util.Polyline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PolylineTest {
    // The canonical example from Google's encoded-polyline docs.
    private val canonical = "_p~iF~ps|U_ulLnnqC_mqNvxq`@"
    private val points = listOf(38.5 to -120.2, 40.7 to -120.95, 43.252 to -126.453)

    @Test fun decodesGoogleExample() {
        val got = Polyline.decode(canonical)
        assertEquals(3, got.size)
        for ((g, want) in got.zip(points)) {
            assertTrue(abs(g.first - want.first) < 1e-5)
            assertTrue(abs(g.second - want.second) < 1e-5)
        }
    }

    @Test fun encodesGoogleExample() = assertEquals(canonical, Polyline.encode(points))

    @Test fun roundTripsALongRoute() {
        // A jagged fake route with enough points to matter.
        val route = (0..500).map { 40.7 + it * 1e-4 to -74.0 - it * 2e-4 }
        val rt = Polyline.decode(Polyline.encode(route))
        assertEquals(route.size, rt.size)
        for ((g, want) in rt.zip(route)) {
            assertTrue(abs(g.first - want.first) < 1e-5)
            assertTrue(abs(g.second - want.second) < 1e-5)
        }
    }

    @Test fun downsampleKeepsEndpointsAndCap() {
        val route = (0..500).map { 40.7 + it * 1e-4 to -74.0 - it * 2e-4 }
        val thin = Polyline.downsample(route, 150)
        assertEquals(150, thin.size)
        assertEquals(route.first(), thin.first())
        assertEquals(route.last(), thin.last())
        // Already-small lists pass through untouched.
        assertEquals(route, Polyline.downsample(route, 501))
    }

    @Test fun thinnedEncodingFitsAStaticMapsUrl() {
        val route = (0..2000).map { 40.7 + it * 1e-4 to -74.0 - it * 2e-4 }
        val enc = Polyline.encode(Polyline.downsample(route, 150))
        assertTrue("encoded length ${enc.length}", enc.length < 3000)
    }
}
