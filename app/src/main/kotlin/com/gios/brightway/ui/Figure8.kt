package com.gios.brightway.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Did the phone just get waved in a figure 8?
 *
 * The magnetometer's hard-iron calibration goes stale in a pocket and the HAL keeps
 * reporting "high confidence" on it, which is how this compass used to open pointing at
 * a wall. Android has no API to ask for a recalibration — the only way in is the wave,
 * and the only way to know it happened is to watch for it.
 *
 * A figure 8 is not "the phone moved". It is a sustained back-and-forth about two
 * different axes at once, so that is what is counted: direction reversals per axis,
 * above a threshold no pocket or footstep reaches, plus a total amount of turning. Two
 * axes must reverse at least [REVERSALS] times each and [RADIANS] radians must go by,
 * all inside [WINDOW_MS] — and the window resets after [IDLE_MS] of stillness, so three
 * separate flicks a minute apart are not a figure 8 either.
 *
 * Pure maths on purpose: no sensor types in here, so it can be tested with numbers.
 */
class Figure8Detector {

    private val lastSign = IntArray(3)
    private val reversals = IntArray(3)
    private var radians = 0f
    private var startMs = 0L
    private var lastMs = 0L

    /** 0f..1f, for a progress ring — how much of a figure 8 has been seen so far. */
    var progress: Float = 0f
        private set

    /** True once, on the sample that completes the gesture. Feed it gyro rad/s. */
    fun onGyro(wx: Float, wy: Float, wz: Float, tMs: Long): Boolean {
        val idle = lastMs != 0L && tMs - lastMs > IDLE_MS
        if (startMs == 0L || idle || tMs - startMs > WINDOW_MS) reset(tMs)
        val dt = ((tMs - lastMs).coerceIn(0, 100)) / 1000f
        lastMs = tMs

        val w = floatArrayOf(wx, wy, wz)
        val speed = sqrt(wx * wx + wy * wy + wz * wz)
        if (speed > IDLE_RATE) radians += speed * dt

        for (i in 0..2) {
            val sign = when {
                w[i] > PEAK_RATE -> 1
                w[i] < -PEAK_RATE -> -1
                else -> 0
            }
            if (sign != 0 && sign != lastSign[i]) {
                if (lastSign[i] != 0) reversals[i]++
                lastSign[i] = sign
            }
        }

        // The two busiest axes: a figure 8 is a wave, not a spin, so the second-best axis
        // has to be doing real work too.
        val sorted = reversals.sortedDescending()
        val turning = (radians / RADIANS).coerceAtMost(1f)
        val waving = (minOf(sorted[0], sorted[1]).toFloat() / REVERSALS).coerceAtMost(1f)
        progress = (turning * 0.4f + waving * 0.6f).coerceIn(0f, 1f)

        val done = sorted[1] >= REVERSALS && radians >= RADIANS
        if (done) reset(0L)
        return done
    }

    private fun reset(tMs: Long) {
        lastSign.fill(0)
        reversals.fill(0)
        radians = 0f
        progress = 0f
        startMs = tMs
        lastMs = tMs
    }

    companion object {
        /** rad/s a swing must exceed to count as deliberate. A brisk walk is under 1. */
        const val PEAK_RATE = 1.3f
        const val IDLE_RATE = 0.35f
        const val REVERSALS = 3
        const val RADIANS = 7f
        const val WINDOW_MS = 9_000L
        const val IDLE_MS = 1_200L
    }
}

/**
 * Whether the compass has earned the right to be believed, for the life of the process.
 *
 * Screen-scoped state would ask for the wave again on every trip between the nav compass
 * and the standalone one, which is the sort of thing that makes people stop using a
 * feature. It is dropped when the HAL itself admits the calibration is gone.
 */
object CompassCalibration {
    @Volatile
    var waved: Boolean = false
        private set

    fun onWaved() { waved = true }

    /** Accuracy 0 or 1 means the old calibration is dead — the wave has to happen again. */
    fun onAccuracy(accuracy: Int) { if (accuracy in 0..1) waved = false }
}

/** Gate state: is the face allowed on screen, and how far along is the wave. */
class Figure8State internal constructor() {
    var waved by mutableStateOf(CompassCalibration.waved)
        internal set

    var progress by mutableFloatStateOf(0f)
        internal set

    /** No gyroscope, no gesture to detect — never hold the compass back on those phones. */
    var detectable by mutableStateOf(true)
        internal set
}

@Composable
fun rememberFigure8(): Figure8State {
    val context = LocalContext.current
    val state = remember { Figure8State() }
    state.waved = CompassCalibration.waved

    DisposableEffect(state.waved) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val gyro = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        state.detectable = gyro != null
        val detector = Figure8Detector()
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                if (e.values.size < 3) return
                val t = e.timestamp / 1_000_000
                if (detector.onGyro(e.values[0], e.values[1], e.values[2], t)) {
                    CompassCalibration.onWaved()
                    state.waved = true
                    state.progress = 0f
                } else if (abs(detector.progress - state.progress) > 0.02f) {
                    state.progress = detector.progress
                }
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit
        }
        // Only while the gesture is still wanted: a gyro at GAME rate is not free.
        if (gyro != null && !state.waved) {
            sm.registerListener(listener, gyro, SensorManager.SENSOR_DELAY_GAME)
        }
        onDispose { sm.unregisterListener(listener) }
    }
    return state
}
