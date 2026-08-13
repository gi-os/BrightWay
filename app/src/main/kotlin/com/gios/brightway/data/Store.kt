package com.gios.brightway.data

import android.content.Context
import android.content.SharedPreferences

/**
 * SharedPreferences is the whole persistence layer. Saved places and recents are small
 * JSON arrays; a database for two lists of a dozen rows is machinery this app doesn't need.
 */
class Store(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("brightway", Context.MODE_PRIVATE)

    /** The user's own Google Maps Platform key, entered by QR. Never ships in the APK. */
    var apiKey: String
        get() = prefs.getString("apiKey", "") ?: ""
        set(v) { prefs.edit().putString("apiKey", v.trim()).apply() }

    /** Colour during navigation (needs the adb WRITE_SECURE_SETTINGS grant). */
    var colorNav: Boolean
        get() = prefs.getBoolean("colorNav", false)
        set(v) { prefs.edit().putBoolean("colorNav", v).apply() }

    /** Nav compass points at the destination (crow flies) instead of the next turn. */
    var compassCrowFlies: Boolean
        get() = prefs.getBoolean("compassCrowFlies", false)
        set(v) { prefs.edit().putBoolean("compassCrowFlies", v).apply() }

    var saved: List<Place>
        get() = Place.listFromJson(prefs.getString("saved", null))
        set(v) { prefs.edit().putString("saved", Place.listToJson(v)).apply() }

    var recents: List<Place>
        get() = Place.listFromJson(prefs.getString("recents", null))
        set(v) { prefs.edit().putString("recents", Place.listToJson(v)).apply() }

    fun addRecent(p: Place) {
        // Newest first, de-duplicated by name+address, capped where the wheel stops being fun.
        recents = (listOf(p) + recents.filterNot {
            it.name == p.name && it.address == p.address
        }).take(12)
    }
}
