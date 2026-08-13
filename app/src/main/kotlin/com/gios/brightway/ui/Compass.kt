package com.gios.brightway.ui

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import com.gios.light.common.theme.Dim
import com.gios.light.common.theme.Faint
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.delay

/**
 * One compass, shared by the standalone screen and the in-nav view.
 *
 * Heading comes from two sources, best one wins:
 *
 *  1. GPS course while actually walking. `fix.bearing` is true north by construction and
 *     completely immune to the magnetometer's moods — and the LPIII's magnetometer has
 *     moods: it reports "high confidence" on stale hard-iron calibration until a
 *     figure-8 shake fixes it. If you're moving, the track is the truth.
 *  2. The rotation vector when standing still, declination-corrected (magnetic → true
 *     north, ~13°W in NYC) and posture-remapped (past ~40° of pitch, heading means
 *     "where the back of the phone points").
 *
 * A raw magnetometer watch runs alongside: field magnitude far outside Earth's 25–65 µT
 * means the sensor is reading a subway rail / radiator / magnet, and the label says so
 * instead of parroting the HAL's confidence.
 */
class HeadingState internal constructor() {
    /** Sensor-derived degrees clockwise from TRUE north; NaN until the first event. */
    internal var sensorAzimuth by mutableFloatStateOf(Float.NaN)

    /** GPS course over ground, NaN unless walking with a fresh fix. */
    internal var gpsCourse by mutableFloatStateOf(Float.NaN)

    /** Rotation vector accuracy, -1 unknown then 0 (unreliable) .. 3 (high). */
    var accuracy by mutableIntStateOf(-1)
        internal set

    /** Raw field magnitude outside the plausible-Earth band. */
    var interference by mutableStateOf(false)
        internal set

    val fromGps: Boolean get() = !gpsCourse.isNaN()

    val azimuthDeg: Float get() = if (fromGps) gpsCourse else sensorAzimuth
}

@Composable
fun rememberHeading(fix: Location?): HeadingState {
    val context = LocalContext.current
    val state = remember { HeadingState() }

    // A 1 Hz tick so a stale fix can expire: Locator only reports after 2 m of movement,
    // so when you stop walking no new fix arrives to tell us the old course is dead.
    var nowMs by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMs = SystemClock.elapsedRealtime()
            delay(1000)
        }
    }
    val fixAgeMs = fix?.let { (nowMs - it.elapsedRealtimeNanos / 1_000_000).coerceAtLeast(0) }
    state.gpsCourse =
        if (fix != null && fixAgeMs != null && fixAgeMs < 3500 &&
            fix.hasBearing() && fix.hasSpeed() && fix.speed > 0.8f
        ) (fix.bearing % 360f + 360f) % 360f
        else Float.NaN

    // Declination drifts by fractions of a degree per year and per city — keying on the
    // whole degree of the fix recomputes it when it could possibly matter and never else.
    val declination = remember(fix?.latitude?.toInt(), fix?.longitude?.toInt()) {
        fix?.let {
            GeomagneticField(
                it.latitude.toFloat(), it.longitude.toFloat(),
                it.altitude.toFloat(), System.currentTimeMillis(),
            ).declination
        } ?: 0f
    }
    DisposableEffect(declination) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val rotation = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val magnet = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        val listener = object : SensorEventListener {
            private val r = FloatArray(9)
            private val r2 = FloatArray(9)
            private val o = FloatArray(3)
            private val vec = FloatArray(4)
            override fun onSensorChanged(e: SensorEvent) {
                when (e.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        // Some HALs ship 5-element rotation vectors and
                        // getRotationMatrixFromVector throws on them. Truncate.
                        val v = if (e.values.size > 4) {
                            System.arraycopy(e.values, 0, vec, 0, 4); vec
                        } else e.values
                        SensorManager.getRotationMatrixFromVector(r, v)
                        SensorManager.getOrientation(r, o)
                        if (abs(Math.toDegrees(o[1].toDouble())) > 40) {
                            SensorManager.remapCoordinateSystem(
                                r, SensorManager.AXIS_X, SensorManager.AXIS_Z, r2,
                            )
                            SensorManager.getOrientation(r2, o)
                        }
                        state.sensorAzimuth =
                            (((Math.toDegrees(o[0].toDouble()) + declination) % 360.0 + 360.0) % 360.0)
                                .toFloat()
                        if (e.accuracy in 0..3) state.accuracy = e.accuracy
                    }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        val m = sqrt(
                            e.values[0] * e.values[0] +
                                e.values[1] * e.values[1] +
                                e.values[2] * e.values[2],
                        )
                        state.interference = m < 20f || m > 70f
                    }
                }
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {
                if (s?.type == Sensor.TYPE_ROTATION_VECTOR) state.accuracy = a
            }
        }
        if (rotation != null) sm.registerListener(listener, rotation, SensorManager.SENSOR_DELAY_UI)
        if (magnet != null) sm.registerListener(listener, magnet, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm.unregisterListener(listener) }
    }
    return state
}

/** Animate an angle the short way round — 359° → 1° must not spin the needle backwards. */
@Composable
private fun animateAngle(target: Float): Float {
    val cont = remember { floatArrayOf(target) }
    val unwrapped = remember(target) {
        cont[0] += ((target - cont[0]) % 360f + 540f) % 360f - 180f
        cont[0]
    }
    return animateFloatAsState(unwrapped, tween(250), label = "angle").value
}

/**
 * The face. A tick ring that rotates with the world (its N sits on true north, letters
 * stay upright), and a needle that points at the target. Monochrome by design — the
 * panel would grey it anyway.
 */
@Composable
fun CompassFace(bearingDeg: Float, headingDeg: Float, modifier: Modifier = Modifier) {
    val needle = animateAngle(((bearingDeg - headingDeg) % 360f + 360f) % 360f)
    val ring = animateAngle(((-headingDeg) % 360f + 360f) % 360f)
    val paint = remember {
        android.graphics.Paint().apply {
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
            typeface = android.graphics.Typeface.MONOSPACE
        }
    }
    Canvas(modifier) {
        val c = center
        val rOuter = size.minDimension / 2f
        rotate(ring, c) {
            for (i in 0 until 24) {
                val major = i % 6 == 0
                val ang = Math.toRadians(i * 15.0)
                val dx = sin(ang).toFloat(); val dy = -cos(ang).toFloat()
                val inner = rOuter - if (major) rOuter * 0.10f else rOuter * 0.05f
                drawLine(
                    color = if (i == 0) Color.White else Color(0xFF3A3A3A),
                    start = Offset(c.x + dx * inner, c.y + dy * inner),
                    end = Offset(c.x + dx * rOuter, c.y + dy * rOuter),
                    strokeWidth = if (i == 0) 6f else if (major) 4f else 2f,
                    cap = StrokeCap.Round,
                )
            }
        }
        // Cardinal letters: positioned by the ring, drawn unrotated so they read upright.
        paint.textSize = rOuter * 0.16f
        val letters = listOf("N", "E", "S", "W")
        for (i in letters.indices) {
            val ang = Math.toRadians(ring + i * 90.0)
            val rr = rOuter * 0.76f
            paint.color = if (i == 0) android.graphics.Color.WHITE else 0xFF6A6A6A.toInt()
            drawContext.canvas.nativeCanvas.drawText(
                letters[i],
                c.x + sin(ang).toFloat() * rr,
                c.y - cos(ang).toFloat() * rr + paint.textSize / 3f,
                paint,
            )
        }
        rotate(needle, c) {
            val rr = rOuter * 0.58f
            val tip = Offset(c.x, c.y - rr)
            drawLine(Color.White, Offset(c.x - rr * 0.30f, c.y + rr * 0.35f), tip, 10f, StrokeCap.Round)
            drawLine(Color.White, Offset(c.x + rr * 0.30f, c.y + rr * 0.35f), tip, 10f, StrokeCap.Round)
            drawLine(Color(0xFF6A6A6A), Offset(c.x, c.y + rr * 0.20f), Offset(c.x, c.y + rr * 0.50f), 4f, StrokeCap.Round)
        }
        drawCircle(Color.White, rOuter * 0.03f, c)
    }
}

/**
 * Where the heading is coming from and whether to believe it, as one quiet line.
 * Priority: GPS course (best) → interference warning (urgent) → the HAL's own confidence.
 */
@Composable
fun AccuracyLabel(heading: HeadingState, modifier: Modifier = Modifier) {
    val (label, col) = when {
        heading.fromGps -> "heading from GPS" to Color.White
        heading.interference -> "magnetic interference — move from metal" to Faint
        else -> when (heading.accuracy) {
            3 -> "compass ok · walk for GPS heading" to Dim
            2 -> "medium confidence" to Dim
            1 -> "low — wave phone in a figure 8" to Faint
            0 -> "unreliable — wave phone in a figure 8" to Faint
            else -> "calibrating…" to Faint
        }
    }
    Text(label, style = MaterialTheme.typography.bodyMedium, color = col, modifier = modifier)
}
