package com.gios.brightway.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.gios.brightway.net.RouteOption
import com.gios.light.common.hw.LocalWheelBus
import com.gios.light.common.theme.Dim
import com.gios.light.common.theme.Faint
import kotlin.math.roundToInt

/**
 * The route on a map. Wheel-driven: turning zooms; the first notch drops out of the
 * whole-route overview into follow mode centred on the GPS fix, and zooming past the
 * bottom returns to overview. One Static Maps GET per (zoom, ~50 m of movement), cached.
 */
@Composable
fun MapView(vm: WayViewModel, route: RouteOption) {
    val fix by vm.locator.fix.collectAsState()
    val dest = vm.destination.collectAsState().value ?: return
    // zoom == null is the auto-fit overview; 16 is a good first street-level step.
    var zoom by remember { mutableStateOf<Int?>(null) }
    var bmp by remember { mutableStateOf<Bitmap?>(null) }

    val bus = LocalWheelBus.current
    LaunchedEffect(bus) {
        bus?.notches?.collect { n ->
            zoom = when {
                n > 0 -> ((zoom ?: 15) + 1).coerceAtMost(19)
                (zoom ?: 0) <= 13 -> null            // zoomed all the way out: overview
                else -> (zoom ?: 15) - 1
            }
        }
    }

    // Re-fetch on zoom change, or in follow mode when the fix moves ~50 m (rounding the
    // coordinate to ~5e-4 deg buckets throttles a 1 Hz GPS stream to a handful of GETs).
    val latBucket = fix?.latitude?.let { (it / 5e-4).roundToInt() }
    val lonBucket = fix?.longitude?.let { (it / 5e-4).roundToInt() }
    LaunchedEffect(zoom, if (zoom != null) latBucket to lonBucket else null) {
        bmp = vm.staticMap.fetch(
            encodedPolyline = route.encodedPolyline,
            centerLat = fix?.latitude, centerLon = fix?.longitude,
            destLat = dest.lat, destLon = dest.lon,
            zoom = zoom,
        ) ?: bmp
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            val b = bmp
            if (b != null) {
                Image(
                    bitmap = b.asImageBitmap(),
                    contentDescription = "route map",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text("loading map…", style = MaterialTheme.typography.bodyLarge, color = Faint)
            }
        }
        Text(
            if (zoom == null) "whole route · wheel to zoom in"
            else "zoom $zoom · wheel down for overview",
            style = MaterialTheme.typography.bodyMedium, color = Dim,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(6.dp),
        )
    }
}
