package com.gios.brightway.ui

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.SystemClock
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gios.light.common.theme.Dim
import com.gios.light.common.theme.Faint
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
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

    /** A figure 8 has been waved and the sensor has since said it is sure. */
    var trusted by mutableStateOf(false)
        internal set

    /** How much of the figure 8 has been seen, 0f..1f. */
    var wavedFraction by mutableFloatStateOf(0f)
        internal set

    /** The figure 8 has been done at least once since the last time the HAL gave up. */
    var waved by mutableStateOf(false)
        internal set

    val fromGps: Boolean get() = !gpsCourse.isNaN()

    val azimuthDeg: Float get() = if (fromGps) gpsCourse else sensorAzimuth

    // --- Smoothing -------------------------------------------------------------------
    //
    // Averaged as a unit vector rather than a number, because degrees do not average:
    // 359 and 1 are two degrees apart and their mean is 180. One exponential filter with
    // a time constant in seconds, so the smoothing is the same however fast the HAL
    // decides to deliver samples, and a snap for real movement — a 90 degree step
    // between two samples is a turn, not noise, and dragging the needle through it is
    // the lag itself.
    private var sx = 0f
    private var sy = 0f
    private var lastNs = 0L

    internal fun pushSensorAzimuth(deg: Float, timestampNs: Long) {
        val rad = Math.toRadians(deg.toDouble())
        val x = sin(rad).toFloat()
        val y = cos(rad).toFloat()
        val first = lastNs == 0L
        val dt = if (first) 0f else ((timestampNs - lastNs) / 1e9f).coerceIn(0f, 0.25f)
        lastNs = timestampNs
        val jump = !first && abs(shortestDelta(sensorAzimuth, deg)) > SNAP_DEG
        val alpha = if (first || jump || dt <= 0f) 1f else 1f - exp(-dt / TAU_S)
        sx += (x - sx) * alpha
        sy += (y - sy) * alpha
        sensorAzimuth =
            ((Math.toDegrees(atan2(sx.toDouble(), sy.toDouble())) % 360.0 + 360.0) % 360.0)
                .toFloat()
    }

    private companion object {
        /** 90 ms: the jitter goes, a head turn does not. */
        const val TAU_S = 0.09f
        const val SNAP_DEG = 75f
    }
}

/** Signed degrees from [from] to [to], -180..180. */
internal fun shortestDelta(from: Float, to: Float): Float {
    if (from.isNaN()) return 0f
    return ((to - from) % 360f + 540f) % 360f - 180f
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
                        val deg =
                            (((Math.toDegrees(o[0].toDouble()) + declination) % 360.0 + 360.0) % 360.0)
                                .toFloat()
                        state.pushSensorAzimuth(deg, e.timestamp)
                        if (e.accuracy in 0..3) {
                            state.accuracy = e.accuracy
                            CompassCalibration.onAccuracy(e.accuracy)
                        }
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
                if (s?.type == Sensor.TYPE_ROTATION_VECTOR) {
                    state.accuracy = a
                    CompassCalibration.onAccuracy(a)
                }
            }
        }
        // GAME, not UI: 50 Hz in gives the filter above something to work with. UI's 16 Hz
        // was half of the old lag on its own — nothing to smooth, so nothing arrived early.
        if (rotation != null) sm.registerListener(listener, rotation, SensorManager.SENSOR_DELAY_GAME)
        if (magnet != null) sm.registerListener(listener, magnet, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm.unregisterListener(listener) }
    }

    // The gate. GPS course is a track over the ground and owes the magnetometer nothing,
    // so it is never held back; a phone with no gyroscope cannot be asked for a gesture
    // nobody can see. Everything else waits for the wave AND for the sensor to say it is
    // sure afterwards, which is the whole point: the wave without the confirmation is
    // just as wrong as before, quietly.
    val wave = rememberFigure8()
    state.wavedFraction = wave.progress
    state.waved = wave.waved || !wave.detectable
    state.trusted = state.fromGps ||
        !wave.detectable ||
        (wave.waved && state.accuracy >= 3 && !state.interference)

    return state
}

/**
 * Animate an angle the short way round — 359 to 1 must not spin the needle backwards.
 *
 * This used to be `tween(250)`, retargeted on every sensor sample. A tween restarts from
 * zero velocity each time it is given a new target, so at 50 Hz the needle only ever
 * travelled the flat opening of the easing curve and then got a fresh curve: it trailed
 * the phone by most of a second and never caught up while turning. A spring keeps its
 * velocity across retargets, which is what a continuously moving target needs.
 *
 * The unwrap is now guarded on the raw value. It mutates state from composition, and a
 * composition that runs twice with the same target used to integrate the same step twice
 * and walk the needle off true.
 */
@Composable
private fun animateAngle(target: Float): Float {
    val acc = remember { AngleUnwrapper() }
    val unwrapped = remember(target) { acc.unwrap(target) }
    return animateFloatAsState(
        unwrapped,
        spring(dampingRatio = 1f, stiffness = 1200f, visibilityThreshold = 0.05f),
        label = "angle",
    ).value
}

/** Turns a 0..360 sequence into a continuous one. Idempotent for a repeated value. */
private class AngleUnwrapper {
    private var last = Float.NaN
    private var continuous = 0f

    fun unwrap(target: Float): Float {
        if (last.isNaN()) {
            last = target
            continuous = target
        } else if (target != last) {
            continuous += shortestDelta(last, target)
            last = target
        }
        return continuous
    }
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
 * The compass, or the reason it is not being shown yet.
 *
 * Every caller wants the same rule and none of them should have to write it: a face that
 * opens pointing at a wall — stale hard-iron calibration, reported by the HAL as high
 * confidence — is worse than no face, because a wrong arrow gets followed. So the arrow
 * is withheld until the phone has been waved in a figure 8 AND the sensor says it is
 * sure of itself afterwards. Walking skips all of it: GPS course is a track, not a field
 * reading. See [rememberHeading] for the gate and [Figure8Detector] for the gesture.
 */
@Composable
fun CompassView(
    bearingDeg: Float,
    heading: HeadingState,
    modifier: Modifier = Modifier,
) {
    if (heading.trusted) {
        val az = if (heading.azimuthDeg.isNaN()) 0f else heading.azimuthDeg
        CompassFace(bearingDeg, az, modifier)
    } else {
        Figure8Prompt(heading, modifier)
    }
}

/**
 * The waiting room: an 8 to copy, filling in as the wave is recognised. Same footprint as
 * the face so nothing jumps when the compass appears.
 */
@Composable
private fun Figure8Prompt(heading: HeadingState, modifier: Modifier = Modifier) {
    val settling = heading.waved && heading.wavedFraction <= 0f
    val phase by rememberInfiniteTransition(label = "eight").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    val traced = animateFloatAsState(heading.wavedFraction, tween(180), label = "traced").value

    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(150.dp)) {
            Canvas(Modifier.size(150.dp)) {
                val w = size.width * 0.34f
                val h = size.height * 0.40f
                val c = center
                fun at(t: Float): Offset {
                    val a = t * 2f * Math.PI.toFloat()
                    return Offset(c.x + sin(2f * a) * w * 0.5f, c.y - cos(a) * h)
                }
                fun path(from: Float, to: Float): Path = Path().apply {
                    val steps = 96
                    for (i in 0..steps) {
                        val o = at(from + (to - from) * i / steps)
                        if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y)
                    }
                }
                drawPath(path(0f, 1f), Color(0xFF2E2E2E), style = Stroke(6f, cap = StrokeCap.Round))
                if (traced > 0.01f) {
                    drawPath(
                        path(0f, traced),
                        Color.White,
                        style = Stroke(6f, cap = StrokeCap.Round),
                    )
                }
                // The demonstration: a dot walking the 8 while nothing else is happening.
                if (traced <= 0.01f && !settling) {
                    drawCircle(Color.White, size.minDimension * 0.035f, at(phase))
                }
            }
        }
        Text(
            if (settling) "hold still — settling" else "wave the phone in a figure 8",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 18.dp, start = 24.dp, end = 24.dp),
        )
        Text(
            if (heading.interference) {
                "and step away from the metal"
            } else {
                "the compass shows up once it is sure"
            },
            style = MaterialTheme.typography.labelSmall,
            color = Faint,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp, start = 24.dp, end = 24.dp),
        )
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
        !heading.waved -> "not calibrated — wave phone in a figure 8" to Faint
        else -> when (heading.accuracy) {
            3 -> "compass ok · walk for GPS heading" to Dim
            2 -> "still settling after the figure 8" to Faint
            1 -> "low — wave phone in a figure 8 again" to Faint
            0 -> "unreliable — wave phone in a figure 8 again" to Faint
            else -> "calibrating…" to Faint
        }
    }
    Text(label, style = MaterialTheme.typography.bodyMedium, color = col, modifier = modifier)
}
