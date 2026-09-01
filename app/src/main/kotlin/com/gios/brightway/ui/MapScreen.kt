package com.gios.brightway.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gios.brightway.net.RouteOption
import com.gios.light.common.hw.LocalWheelBus
import com.gios.light.common.theme.Dim
import com.gios.light.common.theme.Faint
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/**
 * The route on a map. Wheel-driven: turning zooms; the first notch drops out of the
 * whole-route overview into follow mode centred on the GPS fix, and zooming past the
 * bottom returns to overview. One Static Maps GET per (zoom, ~50 m of movement), cached.
 *
 * The map also re-fetches every 10 s with the current fix, so the user marker keeps
 * tracking you even when you're not crossing a rounding bucket — the cache makes a
 * motionless refetch a no-op.
 *
 * When a fetch fails the reason is shown and tappable to retry — never a spinner that
 * lies forever.
 */
@Composable
fun MapView(vm: WayViewModel, route: RouteOption) {
    val fix by vm.locator.fix.collectAsState()
    val dest = vm.destination.collectAsState().value ?: return
    // zoom == null is the auto-fit overview; 16 is a good first street-level step.
    var zoom by remember { mutableStateOf<Int?>(null) }
    var bmp by remember { mutableStateOf<Bitmap?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var tick by remember { mutableIntStateOf(0) }

    // A 10 s heartbeat: in follow mode it recentres on the live fix; in overview it just
    // bumps the key so the refetch re-runs (and hits the cache for the same URL).
    LaunchedEffect(Unit) {
        while (true) {
            delay(10_000)
            tick += 1
        }
    }

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

    // Re-fetch on zoom change, on the 10 s heartbeat, or in follow mode when the fix
    // moves ~50 m (rounding the coordinate to ~5e-4 deg buckets throttles a 1 Hz GPS
    // stream to a handful of GETs).
    val latBucket = fix?.latitude?.let { (it / 5e-4).roundToInt() }
    val lonBucket = fix?.longitude?.let { (it / 5e-4).roundToInt() }
    LaunchedEffect(zoom, tick, if (zoom != null) latBucket to lonBucket else null, retry) {
        val r = vm.staticMap.fetch(
            destLat = dest.lat, destLon = dest.lon,
            encodedPolyline = route.encodedPolyline,
            userLat = fix?.latitude, userLon = fix?.longitude,
            centerLat = fix?.latitude, centerLon = fix?.longitude,
            zoom = zoom,
        )
        if (r.bitmap != null) {
            bmp = r.bitmap
            error = null
        } else {
            error = r.error
        }
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            val b = bmp
            when {
                b != null -> Image(
                    bitmap = b.asImageBitmap(),
                    contentDescription = "route map",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        error ?: "",
                        style = MaterialTheme.typography.bodyMedium, color = Dim,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                    Text(
                        "TAP TO RETRY",
                        style = MaterialTheme.typography.labelLarge, color = Color.White,
                        modifier = Modifier
                            .clickable { retry += 1 }
                            .padding(16.dp),
                    )
                }
                else -> Text("loading map…",
                    style = MaterialTheme.typography.bodyLarge, color = Faint)
            }
        }
        Text(
            when {
                error != null && bmp != null -> "map refresh failed · tap here to retry"
                zoom == null -> "whole route · wheel to zoom in"
                else -> "zoom $zoom · wheel down for overview"
            },
            style = MaterialTheme.typography.bodyMedium, color = Dim,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clickable { retry += 1 }
                .padding(6.dp),
        )
    }
}
