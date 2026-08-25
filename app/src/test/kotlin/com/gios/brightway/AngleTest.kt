package com.gios.brightway

import com.gios.brightway.ui.shortestDelta
import org.junit.Assert.assertEquals
import org.junit.Test

/** The needle must never take the long way round, and 359 to 1 is two degrees. */
class AngleTest {

    @Test
    fun `wraps the short way`() {
        assertEquals(2f, shortestDelta(359f, 1f), 0.001f)
        assertEquals(-2f, shortestDelta(1f, 359f), 0.001f)
        assertEquals(10f, shortestDelta(0f, 10f), 0.001f)
        assertEquals(-90f, shortestDelta(0f, 270f), 0.001f)
    }

    @Test
    fun `half a turn goes one way, consistently`() {
        // Genuinely ambiguous, so it only has to be stable: the needle picks a side and
        // both halves of the face pick the same one.
        assertEquals(-180f, shortestDelta(0f, 180f), 0.001f)
        assertEquals(-180f, shortestDelta(180f, 0f), 0.001f)
    }

    @Test
    fun `no first value, no movement`() {
        assertEquals(0f, shortestDelta(Float.NaN, 123f), 0.001f)
    }
}
