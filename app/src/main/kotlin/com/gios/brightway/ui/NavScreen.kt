package com.gios.brightway.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.gios.brightway.net.Step
import com.gios.brightway.util.ColorMode
import com.gios.brightway.util.Geo
import com.gios.light.common.hw.WheelScroll
import com.gios.light.common.theme.Dim
import com.gios.light.common.theme.Faint

private fun glyph(maneuver: String): String = when {
    maneuver.contains("LEFT") -> "↰"
    maneuver.contains("RIGHT") -> "↱"
    maneuver.contains("UTURN") -> "⤶"
    maneuver == "TRANSIT" -> "◉"
    maneuver.contains("DEPART") || maneuver.contains("STRAIGHT") -> "↑"
    maneuver.contains("ARRIVE") -> "●"
    else -> "↑"
}

/**
 * Turn-by-turn. One big instruction, the live distance to it, and the rest of the trip
 * under a rule — the wheel scrolls the list. The current step advances itself when the
 * fix comes within 20 m of the step's end, so the phone can stay in a pocket between turns.
 */
@Composable
fun NavScreen(vm: WayViewModel, onDone: () -> Unit) {
    val route = vm.chosen.collectAsState().value ?: run { onDone(); return }
    val fix by vm.locator.fix.collectAsState()
    val context = LocalContext.current
    var stepIndex by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    WheelScroll(listState)

    // Colour while navigating, greyscale on the way out. The flip is one secure-settings
    // integer; ungranted it just returns false and the screen stays exactly as legible.
    DisposableEffect(Unit) {
        if (vm.store.colorNav) ColorMode.setColor(context, true)
        onDispose { if (vm.store.colorNav) ColorMode.setColor(context, false) }
    }

    val steps = route.steps
    val current = steps.getOrNull(stepIndex)

    // Distance from the live fix to the end of the current step; arrival advances it.
    val distToNext = fix?.let { f ->
        current?.let { Geo.distanceM(f.latitude, f.longitude, it.endLat, it.endLon) }
    }
    if (distToNext != null && distToNext < 20 && stepIndex < steps.lastIndex) {
        stepIndex += 1
    }

    Column(Modifier.fillMaxSize()) {
        MenuRow("‹ END", detail = Geo.prettyDuration(route.durationS), onClick = onDone)
        Rule()

        if (current != null) {
            Column(Modifier.fillMaxWidth().padding(24.dp)) {
                current.transit?.let { ride ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LineBadge(ride.lineName, ride.lineColorHex, ride.textColorHex, big = true)
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text("toward ${ride.headsign}",
                                style = MaterialTheme.typography.titleMedium, color = Color.White)
                            Text("${ride.stopCount} stops · exit ${ride.exitStop}",
                                style = MaterialTheme.typography.bodyMedium, color = Dim)
                        }
                    }
                } ?: run {
                    Text(glyph(current.maneuver),
                        style = MaterialTheme.typography.displayLarge, color = Color.White)
                    Text(current.instruction,
                        style = MaterialTheme.typography.titleLarge, color = Color.White)
                }
                Text(
                    distToNext?.let { Geo.prettyDistance(it) } ?: "waiting for GPS…",
                    style = MaterialTheme.typography.displaySmall,
                    color = if (distToNext == null) Faint else Color.White,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }

        Rule()
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(steps.size) { i ->
                val s = steps[i]
                StepRow(s, done = i < stepIndex, active = i == stepIndex)
            }
        }
    }
}

@Composable
private fun StepRow(s: Step, done: Boolean, active: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = when {
            active -> Color.White
            done -> Faint
            else -> Dim
        }
        if (s.transit != null) {
            LineBadge(s.transit.lineName, s.transit.lineColorHex, s.transit.textColorHex)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("${s.transit.boardStop} → ${s.transit.exitStop}",
                    style = MaterialTheme.typography.bodyLarge, color = tint)
                Text("${s.transit.stopCount} stops · ${s.transit.departHHMM}–${s.transit.arriveHHMM}",
                    style = MaterialTheme.typography.bodyMedium, color = Faint)
            }
        } else {
            Text(glyph(s.maneuver), style = MaterialTheme.typography.titleLarge, color = tint)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(s.instruction, style = MaterialTheme.typography.bodyLarge, color = tint)
                Text(Geo.prettyDistance(s.distanceM),
                    style = MaterialTheme.typography.bodyMedium, color = Faint)
            }
        }
    }
    Rule()
}

/**
 * The MTA bullet. With colour on (daltonizer released) this is the real line colour from
 * the Routes response; in greyscale it still reads — a grey disc with the letter in it.
 */
@Composable
fun LineBadge(name: String, colorHex: String, textHex: String, big: Boolean = false) {
    val bg = runCatching { Color(android.graphics.Color.parseColor(colorHex)) }
        .getOrDefault(Color(0xFF444444))
    val fg = runCatching { Color(android.graphics.Color.parseColor(textHex)) }
        .getOrDefault(Color.White)
    Box(
        Modifier.size(if (big) 56.dp else 32.dp).clip(CircleShape).background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.take(3),
            style = if (big) MaterialTheme.typography.titleLarge
                else MaterialTheme.typography.bodyMedium,
            color = fg,
        )
    }
}
