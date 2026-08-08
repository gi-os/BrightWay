package com.gios.brightway.util

import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure spherical geometry — no Android imports, so plain kotlinc (and JUnit on the JVM)
 * can exercise it. Everything nav needs to decide "how far, which way, past this turn yet".
 */
object Geo {
    private const val EARTH_M = 6_371_008.8

    /** Great-circle distance in metres. */
    fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1); val p2 = Math.toRadians(lat2)
        val dp = Math.toRadians(lat2 - lat1); val dl = Math.toRadians(lon2 - lon1)
        val a = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
        return 2 * EARTH_M * atan2(sqrt(a), sqrt(1 - a))
    }

    /** Initial bearing from point 1 to point 2, degrees clockwise from true north, 0..360. */
    fun bearingDeg(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1); val p2 = Math.toRadians(lat2)
        val dl = Math.toRadians(lon2 - lon1)
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    /** Feet under 1000 ft, otherwise miles to one decimal — how a person walking thinks. */
    fun prettyDistance(meters: Double): String {
        val feet = meters * 3.28084
        return if (feet < 1000) "${(feet / 10).toInt() * 10} ft"
        else {
            val mi = meters / 1609.344
            if (mi < 10) String.format("%.1f mi", mi) else "${mi.roundToInt()} mi"
        }
    }

    /** "3 min" / "1 hr 12 min" out of seconds. */
    fun prettyDuration(seconds: Long): String {
        val m = (seconds + 30) / 60
        return when {
            m < 1 -> "1 min"
            m < 60 -> "$m min"
            else -> "${m / 60} hr ${m % 60} min"
        }
    }
}
