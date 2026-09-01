package com.gios.brightway.share

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.gios.brightway.nav.NavSession
import com.gios.brightway.util.NavMath
import kotlin.math.roundToInt

/**
 * The current turn, offered to the lock face.
 *
 * BrightControl draws its lock face over LightOS and has no way to know this app is
 * mid-trip: the nav notification is IMPORTANCE_LOW and ongoing precisely so that face
 * ignores it. This provider is the deliberate channel instead —
 * `content://com.gios.brightway.nav/current` answers with exactly one row while
 * navigating and an empty cursor the rest of the time, and BrightControl is built
 * against exactly this shape:
 *
 * | column        | type   |                                                    |
 * |---------------|--------|----------------------------------------------------|
 * | `instruction` | String | the current step, as the nav screen shows it       |
 * | `distanceM`   | Int    | live metres to the step's end (step length pre-fix) |
 * | `etaMinutes`  | Int    | minutes left in the whole trip                     |
 * | `mode`        | String | "WALK" or "TRANSIT"                                |
 * | `lineColor`   | String | "#EE352E"-style hex on a transit step, else NULL   |
 * | `stepIndex`   | Int    | zero-based                                         |
 * | `stepCount`   | Int    |                                                    |
 * | `updatedAt`   | Long   | epoch ms of the last fix or step change            |
 *
 * Same terms as the collection's other bridges: exported, no permission, read-only —
 * every mutating method answers zero. What it reveals is the next street to turn on, on a
 * phone with one user. Failures of any kind answer with the empty cursor; a lock face
 * must never be able to crash the navigation under it.
 */
class NavProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val cursor = MatrixCursor(COLUMNS)
        runCatching {
            if (uri.pathSegments.firstOrNull() != PATH) return cursor
            val st = NavSession.state.value ?: return cursor
            val step = st.route.steps.getOrNull(st.stepIndex) ?: return cursor
            val distM = st.distToNextM ?: step.distanceM
            // The lock face is built against exactly these eight columns, so the off-route
            // hint rides inside the string it already draws rather than in a ninth column
            // an older BrightControl would never read.
            val instruction =
                if (st.offRoute) "${step.instruction} · off route?" else step.instruction
            cursor.addRow(
                arrayOf<Any?>(
                    instruction,
                    distM.roundToInt(),
                    NavMath.etaMinutes(st.route.steps, st.stepIndex, st.distToNextM),
                    st.route.mode,
                    step.transit?.lineColorHex,
                    st.stepIndex,
                    st.route.steps.size,
                    st.updatedMs,
                ),
            )
        }
        return cursor
    }

    override fun getType(uri: Uri): String = "vnd.android.cursor.item/vnd.$AUTHORITY.$PATH"

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    companion object {
        const val AUTHORITY = "com.gios.brightway.nav"
        const val PATH = "current"
        val URI: Uri = Uri.parse("content://$AUTHORITY/$PATH")

        val COLUMNS = arrayOf(
            "instruction",
            "distanceM",
            "etaMinutes",
            "mode",
            "lineColor",
            "stepIndex",
            "stepCount",
            "updatedAt",
        )

        /**
         * A change observers can subscribe to instead of polling. Best-effort: the row is
         * the contract, the ping is a courtesy.
         */
        fun announce(context: Context) {
            runCatching { context.contentResolver.notifyChange(URI, null) }
        }
    }
}
