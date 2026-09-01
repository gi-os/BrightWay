package com.gios.brightway.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gios.brightway.data.Place
import com.gios.brightway.data.Store
import com.gios.brightway.data.Trips
import com.gios.brightway.loc.Locator
import com.gios.brightway.net.ApiKeyMissing
import com.gios.brightway.net.GoogleMaps
import com.gios.brightway.net.RouteOption
import com.gios.brightway.net.StaticMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class WayViewModel(app: Application) : AndroidViewModel(app) {
    val store = Store(app)
    /** The process-wide location loop; the UI and NavService hold leases on the same one. */
    val locator = Locator.get(app)
    private val maps = GoogleMaps { store.apiKey }
    val staticMap = StaticMap { store.apiKey }

    /** The journey log. See [com.gios.brightway.data.Trips]. */
    private val trips = Trips(app)

    val query = MutableStateFlow("")
    val results = MutableStateFlow<List<Place>>(emptyList())
    val destination = MutableStateFlow<Place?>(null)

    /** The search result being looked at on the place map, before any routing. */
    val previewPlace = MutableStateFlow<Place?>(null)
    val options = MutableStateFlow<List<RouteOption>>(emptyList())
    val chosen = MutableStateFlow<RouteOption?>(null)
    val busy = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    val hasKey: Boolean get() = store.apiKey.isNotBlank()

    fun clearError() { _error.value = null }

    fun setApiKey(raw: String) {
        // The companion page can prefix the payload; accept it either way.
        val key = raw.trim().let {
            when {
                it.startsWith("brightway:", true) -> it.substringAfter(':')
                it.startsWith("gmaps:", true) -> it.substringAfter(':')
                else -> it
            }
        }.trim()
        if (key.isNotBlank()) store.apiKey = key
    }

    fun search() {
        val q = query.value.trim()
        if (q.isBlank()) return
        viewModelScope.launch {
            busy.value = true
            runCatching {
                val fix = locator.fix.value
                maps.search(q, fix?.latitude, fix?.longitude)
            }.onSuccess { results.value = it; if (it.isEmpty()) _error.value = "No matches" }
                .onFailure { _error.value = message(it) }
            busy.value = false
        }
    }

    /** Compute WALK and TRANSIT together; the options screen shows both. */
    fun route(to: Place, onReady: () -> Unit) {
        val fix = locator.fix.value ?: run { _error.value = "Waiting for GPS fix"; return }
        destination.value = to
        store.addRecent(to)
        viewModelScope.launch {
            busy.value = true
            runCatching {
                val walk = maps.routes(fix.latitude, fix.longitude, to, "WALK")
                val transit = runCatching {
                    maps.routes(fix.latitude, fix.longitude, to, "TRANSIT")
                }.getOrDefault(emptyList()) // no transit here is normal, not an error
                walk.take(1) + transit.take(3)
            }.onSuccess {
                options.value = it
                if (it.isEmpty()) _error.value = "No route found" else onReady()
            }.onFailure { _error.value = message(it) }
            busy.value = false
        }
    }

    /**
     * A route was chosen, which is the moment a trip begins.
     *
     * The only place where both halves are known: [destination] says where, and the option says how,
     * how far and how long it should take. `route()` is too early — the options have not been
     * fetched and the user may never start one — and the nav screen is too late, because by then the
     * choice is in the past.
     *
     * Recorded for BrightNotebook's day, which had no way to know you went anywhere. See
     * [com.gios.brightway.data.Trips].
     */
    fun choose(option: RouteOption) {
        chosen.value = option
        val to = destination.value ?: return
        runCatching {
            trips.start(
                place = to,
                mode = option.mode,
                plannedS = option.durationS,
                distanceM = option.distanceM,
                now = System.currentTimeMillis(),
            )
        }
    }

    private fun message(t: Throwable): String = when (t) {
        is ApiKeyMissing -> "No API key — scan one in Settings"
        else -> t.message ?: "Network error"
    }
}
