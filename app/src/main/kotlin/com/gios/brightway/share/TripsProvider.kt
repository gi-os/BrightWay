package com.gios.brightway.share

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.gios.brightway.data.Trips
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Where you went on a given day, offered to the rest of the collection.
 *
 * The same shape BrightRecorder's clips provider takes, and for the same reason: BrightNotebook
 * builds a day out of what the other apps know, and this app is the only thing on the phone that
 * knows you walked somewhere. `content://com.gios.brightway.trips/trips/2026-08-25` answers with a
 * row per trip started on that calendar date.
 *
 * Calendar dates, and no sort order beyond the log's own. A journal day begins at four in the
 * morning, and that is BrightNotebook's opinion rather than this app's — so the caller asks for
 * both dates either side and filters, as it already does for every other bridge.
 *
 * No permission, like the collection's other bridges. What it reveals is that you went somewhere,
 * when, and roughly how far, on a phone with one user and a hand-picked set of applications.
 * Read-only: every mutating method answers zero.
 */
class TripsProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val cursor = MatrixCursor(COLUMNS)
        val context = context ?: return cursor
        val segments = uri.pathSegments
        if (segments.size < 2 || segments[0] != PATH) return cursor
        val date = runCatching { LocalDate.parse(segments[1]) }.getOrNull() ?: return cursor
        val zone = ZoneId.systemDefault()
        runCatching {
            Trips(context).all()
                .filter { it.startedMs > 0L }
                .filter { Instant.ofEpochMilli(it.startedMs).atZone(zone).toLocalDate() == date }
                .forEach { trip ->
                    cursor.addRow(
                        arrayOf(
                            trip.startedMs,
                            trip.endedMs,
                            trip.mode,
                            trip.name,
                            trip.address,
                            trip.plannedS,
                            trip.distanceM,
                            if (trip.arrived) 1L else 0L,
                        ),
                    )
                }
        }
        return cursor
    }

    override fun getType(uri: Uri): String = "vnd.android.cursor.dir/vnd.$AUTHORITY.trip"

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    companion object {
        const val AUTHORITY = "com.gios.brightway.trips"
        const val PATH = "trips"

        val COLUMNS = arrayOf(
            "started_ms",
            "ended_ms",
            "mode",
            "name",
            "address",
            "planned_s",
            "distance_m",
            "arrived",
        )
    }
}
