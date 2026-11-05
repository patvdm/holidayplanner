package com.pat.app.holidayplanner.domain

import kotlin.math.roundToInt

/**
 * Roughly how far a walk is and how long it takes, from its stops alone: no directions are asked
 * for, so it costs nothing. Streets are not straight lines, so the distance between stops is
 * stretched by [DETOUR], which is about what city streets add; the time is at an easy sightseeing
 * pace. Rounded, because the precision would be false.
 */
data class WalkEstimate(val metres: Int, val minutes: Int) {

    companion object {
        /** How much longer a walk through streets is than the straight line, in a typical city. */
        const val DETOUR = 1.3

        /** About 4.5 km an hour: walking, not marching. */
        const val METRES_PER_MINUTE = 75.0

        private const val METRE_STEP = 50
        private const val MINUTE_STEP = 5

        /** Null with fewer than two positioned stops, since there is no walk to measure. */
        fun of(stops: List<GeoPoint>): WalkEstimate? {
            if (stops.size < 2) return null
            val straight = stops.zipWithNext { a, b -> Proximity.distanceMetres(a, b) }.sum()
            val metres = straight * DETOUR
            return WalkEstimate(
                metres = roundUp(metres, METRE_STEP),
                minutes = roundUp(metres / METRES_PER_MINUTE, MINUTE_STEP),
            )
        }

        /** To the nearest [step], but never below one step: a short walk is still a walk. */
        private fun roundUp(value: Double, step: Int): Int =
            ((value / step).roundToInt() * step).coerceAtLeast(step)
    }
}
