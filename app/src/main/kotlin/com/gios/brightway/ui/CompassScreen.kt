package com.gios.brightway.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gios.brightway.util.Geo
import com.gios.light.common.theme.Faint

/**
 * As the crow flies. No network, no API key — the rotation vector and a GPS fix are the
 * whole feature, which is exactly what you want when the phone has neither signal nor
 * anything left to prove. Destination is whatever was last routed to, or the first saved
 * place. Heading maths (declination, posture remap) lives in [rememberHeading].
 */
@Composable
fun CompassScreen(vm: WayViewModel) {
    val fix by vm.locator.fix.collectAsState()
    val dest = vm.destination.collectAsState().value ?: vm.store.saved.firstOrNull()
    val heading = rememberHeading(fix)

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

        Text(dest.name, style = MaterialTheme.typography.titleMedium, color = Color.White,
            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
        Text("as the crow flies", style = MaterialTheme.typography.bodyMedium, color = Faint,
            modifier = Modifier.padding(top = 2.dp))
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            CompassView(bearing.toFloat(), heading, Modifier.size(240.dp))
        }
        Text(Geo.prettyDistance(dist), style = MaterialTheme.typography.displaySmall,
            color = Color.White)
        AccuracyLabel(heading, Modifier.padding(top = 4.dp, bottom = 16.dp))
    }
}
