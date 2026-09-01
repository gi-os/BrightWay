package com.gios.brightway.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.gios.brightway.data.Place
import com.gios.brightway.util.ColorMode
import com.gios.light.common.hw.WheelScroll
import com.gios.light.common.theme.Faint

@Composable
fun SettingsScreen(vm: WayViewModel, onScanKey: () -> Unit) {
    val context = LocalContext.current
    val fix by vm.locator.fix.collectAsState()
    var saveName by remember { mutableStateOf("") }
    var colorOn by remember { mutableStateOf(vm.store.colorNav) }
    // Mirrors the pref so add/remove redraw; the pref stays the source of truth on disk.
    var saved by remember { mutableStateOf(vm.store.saved) }
    val listState = rememberLazyListState()
    WheelScroll(listState)

    LazyColumn(state = listState) {
        item { SectionLabel("GOOGLE MAPS KEY") }
        item {
            // Collected, not read from the pref: the QR-scan write lands mid-composition
            // and a plain read would leave "No key" up until something else redrew this row.
            val key by vm.apiKey.collectAsState()
            MenuRow(
                label = if (key.isBlank()) "No key" else "•••• ${key.takeLast(4)}",
                sub = "Generate the QR at gi-os.github.io/BrightWay",
                detail = "SCAN",
                onClick = onScanKey,
            )
        }

        item { SectionLabel("COLOUR") }
        item {
            val granted = ColorMode.granted(context)
            MenuRow(
                label = "Colour while navigating",
                sub = if (granted) "Subway bullets in their real colours"
                else "Needs one adb grant — see the README",
                detail = if (colorOn) "ON" else "OFF",
                dim = !granted,
                onClick = {
                    colorOn = !colorOn
                    vm.store.colorNav = colorOn
                },
            )
        }

        item { SectionLabel("SAVED PLACES") }
        item {
            // Name the spot you are standing on; GPS is the address book here.
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                BasicTextField(
                    value = saveName,
                    onValueChange = { saveName = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                    cursorBrush = SolidColor(Color.White),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        Column {
                            Box {
                                if (saveName.isEmpty()) Text(
                                    "Name this location (e.g. Home)…",
                                    style = MaterialTheme.typography.bodyLarge, color = Faint,
                                )
                                inner()
                            }
                            Rule(Modifier.padding(top = 6.dp))
                        }
                    },
                )
            }
        }
        item {
            MenuRow(
                label = "Save current location",
                sub = fix?.let { "%.5f, %.5f".format(it.latitude, it.longitude) }
                    ?: "Waiting for GPS…",
                dim = fix == null || saveName.isBlank(),
                onClick = {
                    val f = fix ?: return@MenuRow
                    if (saveName.isBlank()) return@MenuRow
                    saved = saved + Place(
                        name = saveName.trim(), address = "saved",
                        lat = f.latitude, lon = f.longitude,
                    )
                    vm.store.saved = saved
                    saveName = ""
                },
            )
        }
        items(saved.size) { i ->
            val p = saved[i]
            MenuRow(p.name, sub = "%.5f, %.5f".format(p.lat, p.lon), detail = "REMOVE",
                onClick = {
                    saved = saved.filterNot {
                        it.name == p.name && it.lat == p.lat && it.lon == p.lon
                    }
                    vm.store.saved = saved
                })
        }

        item { SectionLabel("ABOUT") }
        item {
            MenuRow(
                "BrightWay", dim = true,
                sub = "Walking + subway directions · your own Google key · shake to report a bug",
            )
        }
    }
}
