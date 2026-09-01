package com.gios.brightway.nav

import com.gios.brightway.data.Place
import com.gios.brightway.net.RouteOption
import com.gios.brightway.util.Geo
import com.gios.brightway.util.NavMath
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
    /**
     * The fix has grown the distance for a while — see [com.gios.brightway.util.NavMath.drift].
     * A hint, never a decision: the screen and the lock face ask "off route?", nothing reroutes.
     */
    val offRoute: Boolean = false,
    val startedMs: Long,
    val updatedMs: Long,
)

object NavSession {
    private val _state = MutableStateFlow<NavState?>(null)
    val state: StateFlow<NavState?> = _state

    /**
     * The off-route detector's memory, kept beside the session because every reset of one
     * is a reset of the other: a new route, a step change (wheel or auto), the end of the
     * trip. Not in [NavState] — it is working memory for [driftFix], not something a
     * screen should draw; the screen draws [NavState.offRoute].
     */
    private var drift = NavMath.Drift()

    fun begin(route: RouteOption, destination: Place?, now: Long) {
        drift = NavMath.Drift()
        _state.value = NavState(
            route = route,
            destination = destination,
            stepIndex = 0,
            distToNextM = null,
            offRoute = false,
            startedMs = now,
            updatedMs = now,
        )
    }

    /**
     * One accepted fix's distance to the current step's end, through the off-route rule.
     * Only [com.gios.brightway.nav.NavService.onFix] calls this, and only with fixes that
     * already passed its 40 m accuracy gate — a coarse fix asking "off route?" would be
     * the question asking itself.
     */
    fun driftFix(distToEndM: Double, stepChanged: Boolean): Boolean {
        drift = NavMath.drift(drift, distToEndM, stepChanged)
        return drift.offRoute
    }

    /**
     * The wheel (or the fix) moved the current step. Clamped; a no-op when not navigating.
     *
     * The distance is recomputed against the new step from the caller's fix, because the
     * next fix may never come — a stationary phone gets none — and until then the big
     * number and the lock face would keep showing the old step's metres. No fix means no
     * number: null draws as "waiting for GPS…", which at least is not wrong.
     */
    fun setStep(index: Int, fixLat: Double?, fixLon: Double?) {
        val st = _state.value ?: return
        val last = st.route.steps.lastIndex.coerceAtLeast(0)
        val i = index.coerceIn(0, last)
        val step = st.route.steps.getOrNull(i)
        val d = if (fixLat != null && fixLon != null && step != null) {
            Geo.distanceM(fixLat, fixLon, step.endLat, step.endLon)
        } else null
        // A new step is a new question; whatever the old distance was doing is history.
        drift = NavMath.Drift()
        _state.value = st.copy(
            stepIndex = i,
            distToNextM = d,
            offRoute = false,
            updatedMs = System.currentTimeMillis(),
        )
    }

    fun update(transform: (NavState) -> NavState) {
        _state.value = _state.value?.let(transform)
    }

    fun end() {
        drift = NavMath.Drift()
        _state.value = null
    }
}
