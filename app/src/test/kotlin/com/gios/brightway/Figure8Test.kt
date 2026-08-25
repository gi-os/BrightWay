package com.gios.brightway

import com.gios.brightway.ui.Figure8Detector
import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The gesture that gates the compass. What matters is not that a figure 8 is recognised —
 * it is that nothing else is, because a false positive puts a stale-calibration needle on
 * screen with a confident label under it, which is the bug this gate exists to prevent.
 */
class Figure8Test {

    /** Feed the detector a stream, return the millisecond it fired at, or null. */
    private fun run(
        seconds: Float,
        hz: Int = 50,
        startMs: Long = 1_000L,
        w: (Float) -> Triple<Float, Float, Float>,
    ): Long? {
        val d = Figure8Detector()
        val steps = (seconds * hz).toInt()
        for (i in 0..steps) {
            val t = i.toFloat() / hz
            val (x, y, z) = w(t)
            val ms = startMs + (t * 1000f).toLong()
            if (d.onGyro(x, y, z, ms)) return ms
        }
        return null
    }

    @Test
    fun `a figure 8 fires`() {
        // Two axes swinging at different rates is what the wrist actually does.
        val at = run(4f) { t ->
            Triple(0f, 3f * sin(2 * PI * 1.2 * t).toFloat(), 3f * sin(2 * PI * 0.6 * t).toFloat())
        }
        assertTrue("a proper wave should be recognised", at != null)
        assertTrue("and inside about three seconds of waving", at!! - 1_000L < 3_000L)
    }

    @Test
    fun `walking does not`() {
        // Arm swing: right period, nowhere near the rate of a deliberate wave.
        val at = run(20f) { t ->
            Triple(
                0.6f * sin(2 * PI * 2.0 * t).toFloat(),
                0.8f * sin(2 * PI * 2.0 * t + 1.0).toFloat(),
                0.5f * sin(2 * PI * 1.0 * t).toFloat(),
            )
        }
        assertEquals(null, at)
    }

    @Test
    fun `spinning on one axis does not`() {
        val at = run(10f) { t -> Triple(0f, 4f * sin(2 * PI * 1.2 * t).toFloat(), 0f) }
        assertEquals(null, at)
    }

    @Test
    fun `flicks a minute apart do not add up`() {
        val d = Figure8Detector()
        // One good swing, then a long silence, over and over. Each flick is inside the
        // window; the pauses reset it, which is the whole point of the idle timeout.
        var fired = false
        for (round in 0 until 20) {
            val base = round * 60_000L
            for (i in 0..25) {
                val t = i / 50f
                val v = 4f * sin(2 * PI * 1.2 * t).toFloat()
                if (d.onGyro(v, v, v, base + (t * 1000f).toLong())) fired = true
            }
        }
        assertFalse(fired)
    }

    @Test
    fun `it rearms after firing`() {
        val d = Figure8Detector()
        var count = 0
        for (i in 0..(50 * 12)) {
            val t = i / 50f
            val ms = 1_000L + (t * 1000f).toLong()
            val y = 3f * sin(2 * PI * 1.2 * t).toFloat()
            val z = 3f * sin(2 * PI * 0.6 * t).toFloat()
            if (d.onGyro(0f, y, z, ms)) count++
        }
        assertTrue("12 s of waving is more than one gesture", count >= 2)
    }
}
