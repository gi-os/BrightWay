package com.gios.brightway.util

import kotlin.math.roundToInt

/**
 * Google encoded-polyline codec plus a thinner. Pure Kotlin, no Android imports, so
 * kotlinc and JUnit on the JVM can exercise it the same way [Geo] gets exercised.
 *
 * Exists because Static Maps rejects URLs past ~8k characters and a cross-borough
 * transit route encodes well past that. Thinning the middle to 150 points loses nothing
 * a 640px panel could ever have shown.
 */
object Polyline {
    fun decode(encoded: String): List<Pair<Double, Double>> {
        val out = ArrayList<Pair<Double, Double>>()
        var i = 0; var lat = 0; var lon = 0
        while (i < encoded.length) {
            var shift = 0; var result = 0; var b: Int
            do {
                b = encoded[i++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            lat += if (result and 1 != 0) (result shr 1).inv() else result shr 1
            shift = 0; result = 0
            do {
                b = encoded[i++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            lon += if (result and 1 != 0) (result shr 1).inv() else result shr 1
            out.add(lat / 1e5 to lon / 1e5)
        }
        return out
    }

    fun encode(points: List<Pair<Double, Double>>): String {
        val sb = StringBuilder()
        var prevLat = 0; var prevLon = 0
        for ((lat, lon) in points) {
            val iLat = (lat * 1e5).roundToInt()
            val iLon = (lon * 1e5).roundToInt()
            encodeDiff(iLat - prevLat, sb)
            encodeDiff(iLon - prevLon, sb)
            prevLat = iLat; prevLon = iLon
        }
        return sb.toString()
    }

    private fun encodeDiff(diff: Int, sb: StringBuilder) {
        var v = if (diff < 0) (diff shl 1).inv() else diff shl 1
        while (v >= 0x20) {
            sb.append(((0x20 or (v and 0x1f)) + 63).toChar())
            v = v shr 5
        }
        sb.append((v + 63).toChar())
    }

    /** Evenly thin to at most [max] points; the first and last always survive. */
    fun downsample(points: List<Pair<Double, Double>>, max: Int): List<Pair<Double, Double>> {
        if (points.size <= max || max < 2) return points
        val step = (points.size - 1).toDouble() / (max - 1)
        return (0 until max).map { points[(it * step).roundToInt().coerceAtMost(points.lastIndex)] }
    }
}
