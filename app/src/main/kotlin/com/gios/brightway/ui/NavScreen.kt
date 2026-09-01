package com.gios.brightway.ui

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.gios.brightway.nav.NavService
import com.gios.brightway.nav.NavSession
import com.gios.brightway.net.RouteOption
import com.gios.brightway.net.Step
import com.gios.brightway.share.NavProvider
import com.gios.brightway.util.ColorMode
import com.gios.brightway.util.Geo
import com.gios.light.common.hw.LocalWheelBus
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
 *
 * Since the pocket is the point, this screen no longer owns the trip: [NavService] runs
 * the location loop and the step advance for as long as the trip lasts, screen on or off,
 * and this screen draws whatever [NavSession] says. Coming back to a trip already in
 * progress recomposes onto the right step because the step was never this screen's to lose.
 */
@Composable
fun NavScreen(vm: WayViewModel, onDone: () -> Unit) {
    val route = vm.chosen.collectAsState().value ?: run { onDone(); return }
    val fix by vm.locator.fix.collectAsState()
    val context = LocalContext.current
    // Three-way toggle: steps → map → compass → steps ...
    var viewMode by remember { mutableStateOf("steps") }
    val listState = rememberLazyListState()
    val steps = route.steps

    // The service owns the trip; this screen only asks for it and draws it. Started from
    // here — always foreground, so startForegroundService never hits the background wall.
    // The notification permission (API 33+) is asked once, on the first trip: without it
    // navigation still runs, there is simply no "tap to return" line in the shade.
    val askNotify = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    LaunchedEffect(route) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        NavService.start(context, route, vm.destination.value)
    }

    // Draw from the session while it lives, and from its last words after it ends —
    // arrival stops the service and clears the session, but the screen should keep
    // showing the arrived state rather than snapping back to step one.
    val live by NavSession.state.collectAsState()
    var lastState by remember { mutableStateOf(live) }
    if (live != null) lastState = live
    val session = live ?: lastState
    val stepIndex = session?.stepIndex ?: 0
    val navActive = live != null

    // The wheel drives the step list: a notch past the last row advances to the next
    // step (so a spinning wheel walks you through the turns without reaching for the
    // screen), and the list follows the active step. Compass mode owns the wheel to
    // go back; map mode owns it for zoom — those composables aren't composed here.
    val bus = LocalWheelBus.current
    if (bus != null && viewMode == "steps") {
        val latestIndex by rememberUpdatedState(stepIndex)
        LaunchedEffect(bus) {
            bus.notches.collect { n ->
                NavSession.setStep(if (n > 0) latestIndex + 1 else latestIndex - 1)
                NavProvider.announce(context)
            }
        }
        LaunchedEffect(stepIndex) {
            if (steps.isNotEmpty()) listState.animateScrollToItem(stepIndex)
        }
    }

    // Colour while navigating, greyscale on the way out. The flip is one secure-settings
    // integer; ungranted it just returns false and the screen stays exactly as legible.
    DisposableEffect(Unit) {
        if (vm.store.colorNav) ColorMode.setColor(context, true)
        onDispose { if (vm.store.colorNav) ColorMode.setColor(context, false) }
    }

    // Leaving this screen on purpose — the END row or the back gesture — ends the trip,
    // and the service closes the journey-log entry on its way down. Leaving it because the
    // system reclaimed the activity is not the same gesture at all: the lifecycle is
    // already dead by then, and the service navigates on for the pocket the phone is in.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(Unit) {
        onDispose {
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                NavService.stop(context)
            }
        }
    }

    val current = steps.getOrNull(stepIndex)
    // Live metres to the current step's end — the service's measurement, not a second one.
    val distToNext = session?.distToNextM

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                MenuRow("‹ END", detail = Geo.prettyDuration(route.durationS), onClick = onDone)
            }
            Text(
                if (viewMode == "map") "[ MAP ]" else "MAP",
                style = MaterialTheme.typography.labelLarge,
                color = if (viewMode == "map") Color.White else Faint,
                modifier = Modifier
                    .clickable { viewMode = if (viewMode == "map") "steps" else "map" }
                    .padding(horizontal = 12.dp, vertical = 14.dp),
            )
            Text(
                if (viewMode == "compass") "[ COMPASS ]" else "COMPASS",
                style = MaterialTheme.typography.labelLarge,
                color = if (viewMode == "compass") Color.White else Faint,
                modifier = Modifier
                    .clickable { viewMode = if (viewMode == "compass") "steps" else "compass" }
                    .padding(horizontal = 12.dp, vertical = 14.dp),
            )
        }
        Rule()

        if (viewMode == "map") {
            // The map owns the wheel while it is up (zoom), so the step list below is
            // deliberately not composed at the same time — two WheelScroll consumers
            // fight over the same notches.
            MapView(vm, route)
            return@Column
        }

        if (viewMode == "compass") {
            NavCompass(vm, route, fix, stepIndex, onBack = { viewMode = "steps" })
            return@Column
        }

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
                val arrived = !navActive && steps.isNotEmpty() && stepIndex >= steps.lastIndex
                Text(
                    when {
                        arrived -> "arrived"
                        distToNext != null -> Geo.prettyDistance(distToNext)
                        else -> "waiting for GPS…"
                    },
                    style = MaterialTheme.typography.displaySmall,
                    color = if (distToNext == null && !arrived) Faint else Color.White,
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
 * In-nav compass. Two targets, one toggle: the NEXT TURN (the current step's endpoint)
 * or the DESTINATION as the crow flies. The choice sticks in prefs. The step index is
 * the parent's — NavScreen keeps advancing it from the fix, so this view never disagrees
 * with the step list about where "next" is. Wheel goes back to the steps.
 */
@Composable
fun NavCompass(vm: WayViewModel, route: RouteOption, fix: Location?, currentStep: Int, onBack: () -> Unit) {
    val dest = vm.destination.collectAsState().value
    var crowFlies by remember { mutableStateOf(vm.store.compassCrowFlies) }

    val bus = LocalWheelBus.current
    if (bus != null) {
        LaunchedEffect(bus) { bus.notches.collect { onBack() } }
    }

    val heading = rememberHeading(fix)

    val step = route.steps.getOrNull(currentStep)
    val targetLat = if (crowFlies) dest?.lat ?: step?.endLat else step?.endLat ?: dest?.lat
    val targetLon = if (crowFlies) dest?.lon ?: step?.endLon else step?.endLon ?: dest?.lon
    val targetLabel =
        if (crowFlies) dest?.name ?: "destination"
        else step?.instruction ?: dest?.name ?: "?"

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        // Same furniture as the MAP/COMPASS switch above: brackets mark the active one.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Text(
                if (!crowFlies) "[ NEXT TURN ]" else "NEXT TURN",
                style = MaterialTheme.typography.labelLarge,
                color = if (!crowFlies) Color.White else Faint,
                modifier = Modifier
                    .clickable { crowFlies = false; vm.store.compassCrowFlies = false }
                    .padding(horizontal = 12.dp, vertical = 14.dp),
            )
            Text(
                if (crowFlies) "[ DESTINATION ]" else "DESTINATION",
                style = MaterialTheme.typography.labelLarge,
                color = if (crowFlies) Color.White else Faint,
                modifier = Modifier
                    .clickable { crowFlies = true; vm.store.compassCrowFlies = true }
                    .padding(horizontal = 12.dp, vertical = 14.dp),
            )
        }
        Rule()

        if (fix == null) {
            EmptyState("Waiting for GPS…")
            return@Column
        }
        if (targetLat == null || targetLon == null) {
            EmptyState("No direction yet")
            return@Column
        }

        val bearing = Geo.bearingDeg(fix.latitude, fix.longitude, targetLat, targetLon)
        val dist = Geo.distanceM(fix.latitude, fix.longitude, targetLat, targetLon)

        Text(
            targetLabel,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp).padding(top = 16.dp),
        )
        Text(
            if (crowFlies) "as the crow flies" else "to the next turn",
            style = MaterialTheme.typography.bodyMedium, color = Faint,
            modifier = Modifier.padding(top = 2.dp),
        )
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            CompassView(bearing.toFloat(), heading, Modifier.size(230.dp))
        }
        Text(Geo.prettyDistance(dist), style = MaterialTheme.typography.displaySmall,
            color = Color.White)
        AccuracyLabel(heading, Modifier.padding(top = 4.dp))
        Text("wheel to go back", style = MaterialTheme.typography.labelSmall, color = Faint,
            modifier = Modifier.padding(top = 6.dp, bottom = 12.dp))
    }
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
