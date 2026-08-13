package com.gios.brightway.ui

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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

/**
 * One compass, shared by the standalone screen and the in-nav view. The two fixes that
 * made it point the right way live here so they can never diverge again:
 *
 *  1. Declination. The rotation vector is referenced to MAGNETIC north; GPS bearings are
 *     TRUE north. In NYC that difference is ~13°W — enough to send you up the wrong block
 *     while the arrow swears you're fine. [GeomagneticField] from the current fix closes it.
 *  2. Posture. The flat-case azimuth is meaningless once the phone is held up to be read;
 *     past ~40° of pitch the matrix is remapped so heading means "where the back of the
 *     phone points" — which is how a person actually aims a phone at a street.
 */
class HeadingState {
    /** Degrees clockwise from TRUE north; NaN until the first sensor event. */
    var azimuthDeg by mutableFloatStateOf(Float.NaN)

    /** Rotation vector accuracy, -1 unknown then 0 (unreliable) .. 3 (high). */
    var accuracy by mutableIntStateOf(-1)
}

@Composable
fun rememberHeading(fix: Location?): HeadingState {
    val context = LocalContext.current
    val state = remember { HeadingState() }
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
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val listener = object : SensorEventListener {
            private val r = FloatArray(9)
            private val r2 = FloatArray(9)
            private val o = FloatArray(3)
            override fun onSensorChanged(e: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(r, e.values)
                SensorManager.getOrientation(r, o)
                if (abs(Math.toDegrees(o[1].toDouble())) > 40) {
                    SensorManager.remapCoordinateSystem(
                        r, SensorManager.AXIS_X, SensorManager.AXIS_Z, r2,
                    )
                    SensorManager.getOrientation(r2, o)
                }
                state.azimuthDeg =
                    (((Math.toDegrees(o[0].toDouble()) + declination) % 360.0 + 360.0) % 360.0)
                        .toFloat()
                if (e.accuracy in 0..3) state.accuracy = e.accuracy
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) { state.accuracy = a }
        }
        if (sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
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

/** The rotation vector's own confidence, as one quiet line. */
@Composable
fun AccuracyLabel(accuracy: Int, modifier: Modifier = Modifier) {
    val (label, col) = when (accuracy) {
        3 -> "high confidence" to Color.White
        2 -> "medium confidence" to Dim
        1 -> "low — wave phone in a figure 8" to Faint
        0 -> "unreliable — wave phone in a figure 8" to Faint
        else -> "calibrating…" to Faint
    }
    Text(label, style = MaterialTheme.typography.bodyMedium, color = col, modifier = modifier)
}
