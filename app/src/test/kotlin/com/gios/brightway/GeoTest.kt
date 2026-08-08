package com.gios.brightway

import com.gios.brightway.net.GoogleMaps
import com.gios.brightway.util.Geo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoTest {
    // Union Square (14 St) to Washington Square Arch — about 800 m.
    private val usqLat = 40.7359; private val usqLon = -73.9911
    private val wsqLat = 40.7308; private val wsqLon = -73.9973

    @Test fun distanceIsSane() {
        val d = Geo.distanceM(usqLat, usqLon, wsqLat, wsqLon)
        assertTrue("got $d", d in 700.0..900.0)
    }

    @Test fun bearingSouthWest() {
        val b = Geo.bearingDeg(usqLat, usqLon, wsqLat, wsqLon)
        assertTrue("got $b", b in 180.0..270.0)
    }

    @Test fun zeroDistance() {
        assertEquals(0.0, Geo.distanceM(usqLat, usqLon, usqLat, usqLon), 0.001)
    }

    @Test fun prettyDistances() {
        assertEquals("100 ft", Geo.prettyDistance(30.48))
        assertEquals("0.5 mi", Geo.prettyDistance(804.672))
        assertEquals("12 mi", Geo.prettyDistance(19312.1))
    }

    @Test fun prettyDurations() {
        assertEquals("1 min", Geo.prettyDuration(20))
        assertEquals("18 min", Geo.prettyDuration(18 * 60))
        assertEquals("1 hr 12 min", Geo.prettyDuration(72 * 60))
    }

    @Test fun secondsParse() {
        assertEquals(1234L, GoogleMaps.parseSeconds("1234s"))
        assertEquals(0L, GoogleMaps.parseSeconds(null))
        assertEquals(0L, GoogleMaps.parseSeconds("garbage"))
    }
}
