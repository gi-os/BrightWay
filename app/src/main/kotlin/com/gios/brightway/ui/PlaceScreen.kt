package com.gios.brightway.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.gios.brightway.util.Geo
import com.gios.light.common.hw.LocalWheelBus
import com.gios.light.common.theme.Dim
import com.gios.light.common.theme.Faint

/**
 * Where is it, before you commit to going. A searched place pinned on the map — wheel
 * zooms, your own position is the tiny marker if there's a fix, GO hands the place to
 * the same routing flow every other entry point uses. Same Static Maps plumbing and the
 * same say-why-it-failed rule as the nav map.
 */
@Composable
fun PlaceScreen(vm: WayViewModel, onGo: () -> Unit, onBack: () -> Unit) {
    // A process-death restore lands here with no place to show. Popping the back stack is
    // navigation, and navigation from inside composition is a crash — leave from an effect
    // instead and draw nothing for the frame it takes.
    val place = vm.previewPlace.collectAsState().value
    LaunchedEffect(place == null) { if (place == null) onBack() }
    if (place == null) return
    val fix by vm.locator.fix.collectAsState()
    var zoom by remember { mutableIntStateOf(16) }
    var bmp by remember { mutableStateOf<Bitmap?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }

    val bus = LocalWheelBus.current
    LaunchedEffect(bus) {
        bus?.notches?.collect { n ->
            zoom = (zoom + if (n > 0) 1 else -1).coerceIn(11, 19)
        }
    }

    LaunchedEffect(zoom, retry) {
        val r = vm.staticMap.fetch(
            destLat = place.lat, destLon = place.lon,
            userLat = fix?.latitude, userLon = fix?.longitude,
            centerLat = place.lat, centerLon = place.lon,
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
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { MenuRow("‹ BACK", onClick = onBack) }
            Text(
                "GO ›",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier
                    .clickable { onGo() }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            )
        }
        Rule()

        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(place.name, style = MaterialTheme.typography.titleMedium, color = Color.White)
            if (place.address.isNotBlank()) {
                Text(place.address, style = MaterialTheme.typography.bodyMedium, color = Dim)
            }
            fix?.let {
                Text(
                    Geo.prettyDistance(
                        Geo.distanceM(it.latitude, it.longitude, place.lat, place.lon),
                    ) + " away",
                    style = MaterialTheme.typography.bodyMedium, color = Faint,
                )
            }
        }
        Rule()

        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            val b = bmp
            when {
                b != null -> Image(
                    bitmap = b.asImageBitmap(),
                    contentDescription = "place map",
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
            if (error != null && bmp != null) "map refresh failed · tap here to retry"
            else "zoom $zoom · wheel",
            style = MaterialTheme.typography.bodyMedium, color = Dim,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clickable { retry += 1 }
                .padding(6.dp),
        )
    }
}
