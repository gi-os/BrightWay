package com.gios.brightway.nav

import com.gios.brightway.data.Place
import com.gios.brightway.net.RouteOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The one description of the trip in progress, shared by everything that cares about it.
 *
 * Three readers, one writer. [NavService] drives it from the GPS fix; NavScreen draws it;
 * [com.gios.brightway.share.NavProvider] hands it to BrightControl's lock face. Null means
 * not navigating, and everything downstream — the provider's empty cursor included — takes
 * its meaning from that.
 *
 * In-memory on purpose. A route is a pile of steps fetched minutes ago for a trip measured
 * in minutes; persisting it would invite the one thing this app must never do, which is
 * believe it is navigating when nobody asked it to.
 */
data class NavState(
    val route: RouteOption,
    /** Where we're headed, kept so a rebuilt UI can find its way back to the nav screen. */
    val destination: Place?,
    val stepIndex: Int,
    /** Live metres from the fix to the current step's endpoint; null until a fix lands. */
    val distToNextM: Double?,
    val startedMs: Long,
    val updatedMs: Long,
)

object NavSession {
    private val _state = MutableStateFlow<NavState?>(null)
    val state: StateFlow<NavState?> = _state

    fun begin(route: RouteOption, destination: Place?, now: Long) {
        _state.value = NavState(
            route = route,
            destination = destination,
            stepIndex = 0,
            distToNextM = null,
            startedMs = now,
            updatedMs = now,
        )
    }

    /** The wheel (or the fix) moved the current step. Clamped; a no-op when not navigating. */
    fun setStep(index: Int) {
        val st = _state.value ?: return
        val last = st.route.steps.lastIndex.coerceAtLeast(0)
        _state.value = st.copy(
            stepIndex = index.coerceIn(0, last),
            updatedMs = System.currentTimeMillis(),
        )
    }

    fun update(transform: (NavState) -> NavState) {
        _state.value = _state.value?.let(transform)
    }

    fun end() {
        _state.value = null
    }
}
