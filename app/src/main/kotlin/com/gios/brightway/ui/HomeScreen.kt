package com.gios.brightway.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.gios.light.common.hw.WheelScroll
import com.gios.light.common.theme.Faint

/**
 * Destination entry. An underlined field in the LightOS idiom (no filled containers, no
 * floating labels), then saved places and recents so the common trips are one tap.
 */
@Composable
fun HomeScreen(vm: WayViewModel, onRoutes: () -> Unit, onPlace: (com.gios.brightway.data.Place) -> Unit) {
    val query by vm.query.collectAsState()
    val results by vm.results.collectAsState()
    val busy by vm.busy.collectAsState()
    // Collected so the empty-state flips the moment a key is scanned in Settings.
    val hasKey = vm.apiKey.collectAsState().value.isNotBlank()
    val listState = rememberLazyListState()
    WheelScroll(listState)

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 20.dp)) {
            BasicTextField(
                value = query,
                onValueChange = { vm.query.value = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium.copy(color = Color.White),
                cursorBrush = SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.search() }),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    Column {
                        Box { 
                            if (query.isEmpty()) {
                                Text(
                                    "Where to?",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Faint,
                                )
                            }
                            inner()
                        }
                        Rule(Modifier.padding(top = 8.dp))
                    }
                },
            )
        }

        if (busy) {
            Text(
                "…",
                style = MaterialTheme.typography.titleLarge,
                color = Faint,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }

        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            if (results.isNotEmpty()) {
                item { SectionLabel("RESULTS") }
                items(results.size) { i ->
                    val p = results[i]
                    // A searched place is somewhere you've never been — show it on the
                    // map first. Saved and recents skip the preview; you know where home is.
                    MenuRow(p.name, sub = p.address, onClick = { onPlace(p) })
                }
            }
            val saved = vm.store.saved
            if (saved.isNotEmpty()) {
                item { SectionLabel("SAVED") }
                items(saved.size) { i ->
                    val p = saved[i]
                    MenuRow(p.name, sub = p.address, onClick = { vm.route(p) { onRoutes() } })
                }
            }
            val recents = vm.store.recents
            if (recents.isNotEmpty()) {
                item { SectionLabel("RECENT") }
                items(recents.size) { i ->
                    val p = recents[i]
                    MenuRow(p.name, sub = p.address, dim = true,
                        onClick = { vm.route(p) { onRoutes() } })
                }
            }
            if (results.isEmpty() && saved.isEmpty() && recents.isEmpty()) {
                item {
                    EmptyState(
                        if (hasKey) "Search for a place,\nor save one in Settings"
                        else "Scan your Google Maps key\nin Settings to begin",
                        Modifier.padding(top = 60.dp),
                    )
                }
            }
        }
    }
}

