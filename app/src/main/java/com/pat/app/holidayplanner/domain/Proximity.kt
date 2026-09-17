package com.pat.app.holidayplanner.domain

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** A point on the earth, in decimal degrees. */
data class GeoPoint(val lat: Double, val lng: Double)

private const val EARTH_RADIUS_M = 6_371_000.0

/**
 * Distance and ordering over coordinates.
 *
 * The ordering is a plain nearest neighbour walk, which is not the shortest possible route and is
 * not trying to be. It turns a list someone typed in any order into one that does not double back
 * across a city, and that is the whole job: Google does the real routing afterwards.
 */
object Proximity {

    /** Great-circle distance in metres, which is close enough at city scale. */
    fun distanceMetres(from: GeoPoint, to: GeoPoint): Double {
        val lat1 = Math.toRadians(from.lat)
        val lat2 = Math.toRadians(to.lat)
        val deltaLat = Math.toRadians(to.lat - from.lat)
        val deltaLng = Math.toRadians(to.lng - from.lng)

        val haversine = sin(deltaLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(deltaLng / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(min(1.0, sqrt(haversine)))
    }

    /**
     * Orders items so each is the nearest unvisited one to the last.
     *
     * With no [from], the first item that has a location stays put and the rest follow from it,
     * because the person put it first for a reason. With a [from], usually where they are standing,
     * everything is ordered from there. Items with no coordinates keep their order and go last,
     * since there is nothing to measure them by.
     */
    fun <T> orderByProximity(
        items: List<T>,
        from: GeoPoint? = null,
        location: (T) -> GeoPoint?,
    ): List<T> {
        val located = items.filter { location(it) != null }
        val unlocated = items.filter { location(it) == null }
        if (located.isEmpty()) return items

        val remaining = located.toMutableList()
        val ordered = mutableListOf<T>()

        var cursor = if (from != null) {
            from
        } else {
            // No starting point given, so the first stop anchors the walk.
            val anchor = remaining.removeAt(0)
            ordered += anchor
            location(anchor)!!
        }

        while (remaining.isNotEmpty()) {
            val next = remaining.minBy { distanceMetres(cursor, location(it)!!) }
            remaining.remove(next)
            ordered += next
            cursor = location(next)!!
        }

        return ordered + unlocated
    }
}
