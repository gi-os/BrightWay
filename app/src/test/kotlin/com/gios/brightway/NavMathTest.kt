package com.gios.brightway

import com.gios.brightway.net.Step
import com.gios.brightway.util.NavMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavMathTest {

    private fun step(distanceM: Double, durationS: Long) = Step(
        instruction = "walk",
        maneuver = "",
        distanceM = distanceM,
        durationS = durationS,
        endLat = 0.0,
        endLon = 0.0,
    )

    // ------------------------------------------------------------------ advanced

    @Test fun advancesUnderTwentyMetres() {
        assertEquals(1, NavMath.advanced(0, 3, 19.9))
    }

    @Test fun holdsAtTwentyMetresAndBeyond() {
        assertEquals(0, NavMath.advanced(0, 3, 20.0))
        assertEquals(0, NavMath.advanced(0, 3, 350.0))
    }

    @Test fun neverAdvancesPastTheLastStep() {
        assertEquals(3, NavMath.advanced(3, 3, 0.0))
    }

    // ------------------------------------------------------------------ arrived

    @Test fun arrivedOnTheLastStepWithinTheRadius() {
        assertTrue(NavMath.arrived(3, 3, 19.9))
        assertFalse(NavMath.arrived(3, 3, 20.0))
    }

    @Test fun notArrivedBeforeTheLastStep() {
        assertFalse(NavMath.arrived(1, 3, 0.0))
    }

    @Test fun emptyRouteNeverArrives() {
        assertFalse(NavMath.arrived(0, -1, 0.0))
    }

    @Test fun advanceOntoAShortLastStepArrivesOnTheSameFix() {
        // One fix ends the penultimate step and lands within the radius of the last one.
        // A stationary phone gets no further fixes, so arrival must be decided right here —
        // this is the pair of calls NavService makes in its advance branch.
        val next = NavMath.advanced(2, 3, 5.0)
        assertEquals(3, next)
        assertTrue(NavMath.arrived(next, 3, 8.0))
    }

    @Test fun advanceOntoALongLastStepDoesNotArrive() {
        val next = NavMath.advanced(2, 3, 5.0)
        assertEquals(3, next)
        assertFalse(NavMath.arrived(next, 3, 350.0))
    }

    // ------------------------------------------------------------------ etaMinutes

    @Test fun emptyRouteIsZero() {
        assertEquals(0, NavMath.etaMinutes(emptyList(), 0, null))
    }

    @Test fun noFixCountsEveryStepInFull() {
        // 60 s + 120 s + 300 s = 8 min.
        val steps = listOf(step(80.0, 60), step(150.0, 120), step(400.0, 300))
        assertEquals(8, NavMath.etaMinutes(steps, 0, null))
    }

    @Test fun currentStepScalesByRemainingDistance() {
        // Halfway through a 200 s step, with 300 s of trip behind it: 100 + 300 = 400 s → 7 min.
        val steps = listOf(step(100.0, 200), step(400.0, 300))
        assertEquals(7, NavMath.etaMinutes(steps, 0, 50.0))
    }

    @Test fun lastStepNearlyDoneRoundsUpToOneMinute() {
        // 5 m left of a 100 m step: 30 s * 0.05 = 1.5 s. Not yet arrived, so never "0 min".
        val steps = listOf(step(100.0, 30))
        assertEquals(1, NavMath.etaMinutes(steps, 0, 5.0))
    }

    @Test fun overshootClampsToTheStepItself() {
        // A fix further from the endpoint than the step is long (GPS wander, a detour):
        // the step counts once, not one-point-six times. 60 + 60 = 2 min, not more.
        val steps = listOf(step(100.0, 60), step(100.0, 60))
        assertEquals(2, NavMath.etaMinutes(steps, 0, 160.0))
    }

    @Test fun zeroLengthStepDoesNotDivide() {
        // Transit steps can measure oddly; a zero-length current step must not NaN the trip.
        val steps = listOf(step(0.0, 90), step(200.0, 120))
        assertEquals(4, NavMath.etaMinutes(steps, 0, 500.0)) // far away: 90 + 120 = 210 s
        assertEquals(2, NavMath.etaMinutes(steps, 0, 5.0)) // on top of it: 0 + 120 = 120 s
    }

    @Test fun indexOutOfRangeIsClamped() {
        val steps = listOf(step(100.0, 60))
        assertEquals(1, NavMath.etaMinutes(steps, 7, null))
        assertEquals(1, NavMath.etaMinutes(steps, -2, null))
    }
}
