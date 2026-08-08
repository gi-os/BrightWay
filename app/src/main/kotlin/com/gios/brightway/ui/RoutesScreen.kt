package com.gios.brightway.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.gios.brightway.net.RouteOption
import com.gios.brightway.util.Geo
import com.gios.light.common.hw.WheelScroll

/** The choice: one walking route, up to three transit itineraries. */
@Composable
fun RoutesScreen(vm: WayViewModel, onChosen: () -> Unit, onBack: () -> Unit) {
    val options by vm.options.collectAsState()
    val dest by vm.destination.collectAsState()
    val listState = rememberLazyListState()
    WheelScroll(listState)

    Column(Modifier.fillMaxSize()) {
        MenuRow("‹ ${dest?.name ?: "Routes"}", onClick = onBack)
        Rule()
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            val walks = options.filter { it.mode == "WALK" }
            val transits = options.filter { it.mode == "TRANSIT" }
            if (walks.isNotEmpty()) {
                item { SectionLabel("WALK") }
                items(walks.size) { i -> OptionRow(walks[i]) { vm.choose(it); onChosen() } }
            }
            if (transits.isNotEmpty()) {
                item { SectionLabel("SUBWAY + BUS") }
                items(transits.size) { i -> OptionRow(transits[i]) { vm.choose(it); onChosen() } }
            }
            if (options.isEmpty()) item { EmptyState("No routes") }
        }
    }
}

@Composable
private fun OptionRow(o: RouteOption, onPick: (RouteOption) -> Unit) {
    val rides = o.steps.mapNotNull { it.transit }
    val sub = if (rides.isEmpty()) {
        Geo.prettyDistance(o.distanceM)
    } else {
        val first = rides.first()
        "${first.departHHMM} from ${first.boardStop}".trim()
    }
    MenuRow(
        label = if (rides.isEmpty()) "Walk · ${o.summary}" else o.summary,
        sub = sub,
        detail = Geo.prettyDuration(o.durationS),
        onClick = { onPick(o) },
    )
}
