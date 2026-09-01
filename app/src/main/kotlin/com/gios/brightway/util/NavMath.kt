package com.gios.brightway.util

import com.gios.brightway.net.Step
import kotlin.math.ceil

/**
 * The decisions navigation makes out of a fix, kept pure — no Android imports, so plain
 * JUnit on the JVM can exercise them, the way [Geo] already works.
 */
object NavMath {

    /** Within this many metres of a step's endpoint, the step is done. */
    const val ARRIVE_M = 20.0

    /**
     * The step auto-advance rule, exactly as the nav screen has always applied it:
     * under [ARRIVE_M] of the current step's end and not already on the last step, move on.
     * Reaching the last step's endpoint is arrival, which is the caller's problem —
     * this function never advances past the end.
     */
    fun advanced(stepIndex: Int, lastIndex: Int, distToEndM: Double): Int =
        if (distToEndM < ARRIVE_M && stepIndex < lastIndex) stepIndex + 1 else stepIndex

    /**
     * Arrival: standing on the last step and within [ARRIVE_M] of its end. Checked after
     * [advanced] as well as instead of it, because a short final step can be entered and
     * finished by the same fix — and a stationary phone may never deliver another one to
     * finish it with. An empty route (lastIndex < 0) never arrives; it never started.
     */
    fun arrived(stepIndex: Int, lastIndex: Int, distToEndM: Double): Boolean =
        lastIndex >= 0 && stepIndex >= lastIndex && distToEndM < ARRIVE_M

    /**
     * Minutes left in the trip, for a lock face that has room for one number.
     *
     * The steps ahead count at face value — their durations came from the Routes API and
     * are the best guess anyone has. The current step counts by how much of it remains,
     * scaled by the live distance: half the metres left means half the minutes left. That
     * is a straight-line approximation of a crooked street, which is fine for a number
     * that exists to answer "roughly how much longer?".
     *
     * Rounded up, and never zero while there is anything left to do — "0 min" reads as
     * arrived, and the only thing allowed to say that is arrival.
     */
    fun etaMinutes(steps: List<Step>, stepIndex: Int, distToNextM: Double?): Int {
        if (steps.isEmpty()) return 0
        val i = stepIndex.coerceIn(0, steps.lastIndex)
        val current = steps[i]
        val fraction = when {
            distToNextM == null -> 1.0
            current.distanceM <= 0.0 -> if (distToNextM < ARRIVE_M) 0.0 else 1.0
            else -> (distToNextM / current.distanceM).coerceIn(0.0, 1.0)
        }
        var remainingS = current.durationS * fraction
        for (k in i + 1..steps.lastIndex) remainingS += steps[k].durationS
        return if (remainingS <= 0.0) 0 else ceil(remainingS / 60.0).toInt().coerceAtLeast(1)
    }
}
