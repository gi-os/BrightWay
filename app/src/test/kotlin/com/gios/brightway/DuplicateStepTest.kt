package com.gios.brightway

import com.gios.brightway.net.GoogleMaps
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
/**
 * Regression for the "first step, then the first step again" bug: the Routes API
 * prepends a zero-length DEPART step at the origin whose instruction is identical to
 * the real first step. Walking steps with zero distance are navigational noise and
 * must be dropped; so must consecutive walking steps that repeat the same words.
 */
class DuplicateStepTest {

    private val maps = GoogleMaps { "" }

    private fun route(vararg stepJsons: JSONObject): JSONObject = JSONObject()
        .put("duration", "300s")
        .put("distanceMeters", 500)
        .put("polyline", JSONObject().put("encodedPolyline", "xyz"))
        .put("legs", org.json.JSONArray().put(
            JSONObject().put("steps", org.json.JSONArray(stepJsons.toList()))
        ))

    private fun walkStep(
        instruction: String,
        dist: Double,
        maneuver: String = "DEPART",
        lat: Double = 40.72,
        lon: Double = -73.99,
    ) = JSONObject()
        .put("distanceMeters", dist)
        .put("staticDuration", "120s")
        .put("startLocation", JSONObject().put("latLng", JSONObject()
            .put("latitude", lat).put("longitude", lon)))
        .put("endLocation", JSONObject().put("latLng", JSONObject()
            .put("latitude", lat + 0.001).put("longitude", lon + 0.001)))
        .put("navigationInstruction", JSONObject()
            .put("maneuver", maneuver).put("instructions", instruction))

    @Test
    fun zeroLengthDepartStepIsNotDuplicated() {
        // Google prepends the zero-length origin "depart" step with the SAME street text
        // as the real first step. Both used to survive parsing -> the list read the first
        // step twice. The zero-length one must go.
        val zeroLength = walkStep("Head east on Grand St toward Chrystie St", 0.0)
        val realFirst = walkStep("Head east on Grand St toward Chrystie St", 180.0)
        val turn = walkStep("Turn left onto Chrystie St", 90.0, maneuver = "TURN_LEFT")

        val r = maps.parseRoute(route(zeroLength, realFirst, turn), "WALK")
        val steps = r.steps

        assertEquals(2, steps.size)
        assertEquals("Head east on Grand St toward Chrystie St", steps[0].instruction)
        assertEquals(180.0, steps[0].distanceM, 0.001)
        assertEquals("Turn left onto Chrystie St", steps[1].instruction)
    }

    @Test
    fun consecutiveIdenticalWalkingInstructionsAreCollapsed() {
        // Belt and braces: even a non-zero "Continue onto <street>" that repeats the
        // previous turn's words must not appear twice.
        val first = walkStep("Head north on Essex St toward Delancey St", 60.0)
        val repeated = walkStep("Head north on Essex St toward Delancey St", 40.0, maneuver = "STRAIGHT")
        val turn = walkStep("Turn right onto Rivington St", 120.0, maneuver = "TURN_RIGHT")

        val steps = maps.parseRoute(route(first, repeated, turn), "WALK").steps

        assertEquals(2, steps.size)
        assertEquals("Head north on Essex St toward Delancey St", steps[0].instruction)
        assertEquals("Turn right onto Rivington St", steps[1].instruction)
    }

    @Test
    fun zeroLengthStepsBeforeTransitAreDroppedButTransitStays() {
        val walk = walkStep("Walk to the station", 0.0)
        val transit = JSONObject()
            .put("distanceMeters", 400)
            .put("staticDuration", "240s")
            .put("endLocation", JSONObject().put("latLng", JSONObject()
                .put("latitude", 40.73).put("longitude", -73.98)))
            .put("transitDetails", JSONObject()
                .put("headsign", "Brooklyn Bridge")
                .put("stopCount", 3)
                .put("transitLine", JSONObject().put("nameShort", "J"))
                .put("stopDetails", JSONObject()
                    .put("departureStop", JSONObject().put("name", "Essex St"))
                    .put("arrivalStop", JSONObject().put("name", "Delancey St"))))

        val steps = maps.parseRoute(route(walk, transit), "TRANSIT").steps

        assertEquals(1, steps.size)
        assertEquals("TRANSIT", steps[0].maneuver)
    }
}
