package com.gios.brightway.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Where you went, kept.
 *
 * This app knew every trip and remembered none of them. `recents` looks like a history and is not:
 * it is a deduplicated list of twelve destinations with no times on it, so a second walk to the
 * same place overwrites the first and a day's travel is unrecoverable an hour later.
 *
 * What made that worth fixing is BrightNotebook, whose day is assembled out of what the other apps
 * know — where you were, what you played, who you talked to. "Walked to Union Square, 18 minutes"
 * is the most obviously missing line on it, and this app is the only thing on the phone that can
 * say it.
 *
 * ### Shape
 *
 * A trip is opened when a route is chosen and closed when navigation ends. Both halves are needed
 * and neither is enough: the choice knows where and how and how long it *should* take, and only the
 * ending knows whether you got there and what it actually took.
 *
 * Kept as JSON in the same `SharedPreferences` as everything else here, because a database for a
 * list this size is machinery this app does not need — the file's own argument, and it still holds.
 * Capped at [KEEP] entries, which is a few weeks of ordinary use and bounded whatever happens.
 */
data class Trip(
    val startedMs: Long,
    /** When navigation ended. Zero while it is still running. */
    val endedMs: Long,
    /** "WALK" or "TRANSIT", as the Routes API names them. */
    val mode: String,
    val name: String,
    val address: String,
    /**
     * What the route was predicted to take, in seconds, and how far — both as the Routes API
     * hands them over. Narrowing a duration to an Int here would be inventing a precision
     * nobody asked for and losing one nobody would notice until a very long walk.
     */
    val plannedS: Long,
    val distanceM: Double,
    /**
     * Whether the last step was reached before navigation ended.
     *
     * Ending navigation and giving up on it are the same gesture in this app — there is one END row
     * — so arrival is inferred from having reached the final step. Recorded rather than assumed,
     * because "walked to Union Square" and "set off towards Union Square" are different days.
     */
    val arrived: Boolean,
) {
    /** How long it actually took, or null while it is still running. */
    val tookS: Int? get() = if (endedMs > startedMs) ((endedMs - startedMs) / 1000L).toInt() else null

    fun toJson(): JSONObject = JSONObject()
        .put("startedMs", startedMs)
        .put("endedMs", endedMs)
        .put("mode", mode)
        .put("name", name)
        .put("address", address)
        .put("plannedS", plannedS)
        .put("distanceM", distanceM)
        .put("arrived", arrived)

    companion object {
        fun fromJson(o: JSONObject): Trip = Trip(
            startedMs = o.optLong("startedMs"),
            endedMs = o.optLong("endedMs"),
            mode = o.optString("mode"),
            name = o.optString("name"),
            address = o.optString("address"),
            plannedS = o.optLong("plannedS"),
            distanceM = o.optDouble("distanceM"),
            arrived = o.optBoolean("arrived"),
        )
    }
}

/** The journey log. One open trip at a time, because a phone can only be going one place. */
class Trips(context: Context) {

    private val prefs = context.getSharedPreferences("brightway", Context.MODE_PRIVATE)

    fun all(): List<Trip> = runCatching {
        val arr = JSONArray(prefs.getString(KEY, null) ?: return emptyList())
        (0 until arr.length()).map { Trip.fromJson(arr.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    /**
     * A route was chosen. Opens a trip, newest first.
     *
     * An open trip already sitting there is closed as it stands rather than dropped: choosing a new
     * route without ending the last one is a change of mind, and a change of mind at 2:14 is still
     * the fact that you were going somewhere at 2:14.
     */
    fun start(place: Place, mode: String, plannedS: Long, distanceM: Double, now: Long) {
        val open = all().firstOrNull()?.takeIf { it.endedMs == 0L }
        val rest = if (open != null) listOf(open.copy(endedMs = now)) + all().drop(1) else all()
        val trip = Trip(
            startedMs = now,
            endedMs = 0L,
            mode = mode,
            name = place.name,
            address = place.address,
            plannedS = plannedS,
            distanceM = distanceM,
            arrived = false,
        )
        write(listOf(trip) + rest)
    }

    /**
     * Navigation ended. Closes the open trip, if there is one.
     *
     * Silent when there is not, which is the ordinary case for a screen being left twice: the
     * alternative is a trip with no beginning, which reads on a timeline as having arrived
     * somewhere without setting off.
     */
    fun finish(arrived: Boolean, now: Long) {
        val list = all()
        val open = list.firstOrNull()?.takeIf { it.endedMs == 0L } ?: return
        write(listOf(open.copy(endedMs = now, arrived = arrived)) + list.drop(1))
    }

    private fun write(list: List<Trip>) {
        val trimmed = list.take(KEEP)
        val json = JSONArray().apply { trimmed.forEach { put(it.toJson()) } }.toString()
        prefs.edit().putString(KEY, json).apply()
    }

    private companion object {
        const val KEY = "trips"

        /** A few weeks of ordinary use, and bounded whatever happens. */
        const val KEEP = 120
    }
}
