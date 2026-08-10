package com.gios.brightway.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gios.brightway.util.Geo
import com.gios.light.common.theme.Dim
import com.gios.light.common.theme.Faint

/**
 * As the crow flies. No network, no API key — the rotation vector and a GPS fix are the
 * whole feature, which is exactly what you want when the phone has neither signal nor
 * anything left to prove. Destination is whatever was last routed to, or the first saved
 * place.
 */
@Composable
fun CompassScreen(vm: WayViewModel) {
    val context = LocalContext.current
    val fix by vm.locator.fix.collectAsState()
    val dest = vm.destination.collectAsState().value ?: vm.store.saved.firstOrNull()

    var azimuth by remember { mutableFloatStateOf(0f) }
    var accuracy by remember { mutableIntStateOf(-1) }
    DisposableEffect(Unit) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val listener = object : SensorEventListener {
            private val r = FloatArray(9)
            private val o = FloatArray(3)
            override fun onSensorChanged(e: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(r, e.values)
                SensorManager.getOrientation(r, o)
                azimuth = Math.toDegrees(o[0].toDouble()).toFloat()
                accuracy = e.accuracy
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) { accuracy = a }
        }
        if (sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm.unregisterListener(listener) }
    }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (dest == null || fix == null) {
            EmptyState(
                if (dest == null) "Route somewhere first,\nor save a place in Settings"
                else "Waiting for GPS…",
            )
            return@Column
        }
        val bearing = Geo.bearingDeg(fix!!.latitude, fix!!.longitude, dest.lat, dest.lon)
        val dist = Geo.distanceM(fix!!.latitude, fix!!.longitude, dest.lat, dest.lon)
        // Screen-relative pointing: world bearing minus which way the phone faces.
        val arrow = ((bearing - azimuth) + 360.0) % 360.0

        Text(dest.name, style = MaterialTheme.typography.titleMedium, color = Color.White,
            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(220.dp)) {
                val c = center
                val rOuter = size.minDimension / 2f
                drawCircle(Color(0xFF262626), rOuter, c, style = Stroke(2f))
                rotate(arrow.toFloat(), c) {
                    val tip = Offset(c.x, c.y - rOuter * 0.72f)
                    val tailL = Offset(c.x - rOuter * 0.22f, c.y + rOuter * 0.38f)
                    val tailR = Offset(c.x + rOuter * 0.22f, c.y + rOuter * 0.38f)
                    val stroke = Stroke(width = 10f, cap = StrokeCap.Round)
                    drawLine(Color.White, tailL, tip, stroke.width, StrokeCap.Round)
                    drawLine(Color.White, tailR, tip, stroke.width, StrokeCap.Round)
                    drawLine(Color.White, Offset(c.x, c.y + rOuter * 0.5f), tip, 4f, StrokeCap.Round)
                }
            }
        }
        Text(Geo.prettyDistance(dist), style = MaterialTheme.typography.displaySmall,
            color = Color.White)
        Text("as the crow flies", style = MaterialTheme.typography.bodyMedium, color = Faint,
            modifier = Modifier.padding(bottom = 4.dp))
        val (label, col) = when (accuracy) {
            3 -> "high confidence" to Color.White
            2 -> "medium confidence" to Dim
            1 -> "low — calibrate?" to Faint
            0 -> "unreliable — wave phone" to Faint
            else -> "calibrating…" to Faint
        }
        Text(label, style = MaterialTheme.typography.bodyMedium, color = col,
            modifier = Modifier.padding(bottom = 16.dp))
    }
}
