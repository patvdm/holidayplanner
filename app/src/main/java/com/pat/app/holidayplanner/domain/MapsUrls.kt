package com.pat.app.holidayplanner.domain

import java.net.URLEncoder
import java.util.Locale

private const val SEARCH_BASE = "https://www.google.com/maps/search/?api=1"
private const val DIR_BASE = "https://www.google.com/maps/dir/?api=1"

/**
 * One stop on a route. A place id pins the exact business when Google gave us one; the label is
 * what Maps searches for when there are no coordinates.
 */
data class RoutePoint(
    val label: String,
    val lat: Double? = null,
    val lng: Double? = null,
    val googlePlaceId: String = "",
)

/**
 * Google Maps universal URLs. Opened with ACTION_VIEW, which hands them to the Maps app when it is
 * installed and to the browser otherwise.
 *
 * Coordinates are formatted with [Double.toString], which never uses a comma as the decimal
 * separator, so the link is the same on a device set to Dutch.
 */
object MapsUrls {

    /** Origin, destination and the nine waypoints Maps allows between them. */
    const val MAX_ROUTE_STOPS = 11

    private const val PIPE = "|"

    /**
     * A link to one spot. Coordinates identify the point, the place id pins the exact business,
     * and a spot with neither falls back to searching for its name and where it is.
     */
    fun spot(
        lat: Double?,
        lng: Double?,
        googlePlaceId: String = "",
        name: String = "",
        cityName: String = "",
        countryName: String = "",
    ): String {
        val query = if (lat != null && lng != null) {
            "$lat,$lng"
        } else {
            listOf(name, cityName, countryName).filter { it.isNotBlank() }.joinToString(", ")
        }
        val placeIdParam = if (googlePlaceId.isNotBlank()) "&query_place_id=${encode(googlePlaceId)}" else ""
        return "$SEARCH_BASE&query=${encode(query)}$placeIdParam"
    }

    /**
     * Directions through every stop in order, or null for a route too short to be one.
     *
     * Maps takes an origin, a destination and up to nine waypoints between them, so a longer list
     * has to be split first ([splitForMaps]). Place ids are sent per end when known, but the
     * waypoint ids are all or nothing: Maps matches them to the waypoints by position, so a partial
     * list would pin the wrong stops.
     */
    fun route(stops: List<RoutePoint>, travelMode: TravelMode = TravelMode.WALKING): String? {
        if (stops.size < 2) return null

        val origin = stops.first()
        val destination = stops.last()
        val waypoints = stops.subList(1, stops.size - 1)

        return buildString {
            append(DIR_BASE)
            append("&origin=").append(encode(point(origin)))
            append(placeIdParam("origin_place_id", origin.googlePlaceId))
            append("&destination=").append(encode(point(destination)))
            append(placeIdParam("destination_place_id", destination.googlePlaceId))
            if (waypoints.isNotEmpty()) {
                append("&waypoints=").append(waypoints.joinToString(PIPE) { encode(point(it)) })
                if (waypoints.all { it.googlePlaceId.isNotBlank() }) {
                    append("&waypoint_place_ids=")
                    append(waypoints.joinToString(PIPE) { encode(it.googlePlaceId) })
                }
            }
            append("&travelmode=").append(travelMode.name.lowercase(Locale.ROOT))
        }
    }

    /**
     * Directions to one place, starting wherever you happen to be: Maps fills the origin in from
     * the phone, which is what you want when you are already out and heading to the next stop.
     */
    fun directionsTo(stop: RoutePoint, travelMode: TravelMode = TravelMode.WALKING): String = buildString {
        append(DIR_BASE)
        append("&destination=").append(encode(point(stop)))
        append(placeIdParam("destination_place_id", stop.googlePlaceId))
        append("&travelmode=").append(travelMode.name.lowercase(Locale.ROOT))
    }

    /**
     * Cuts a list of stops into routes Maps will accept.
     *
     * Each part ends where the next one begins, so walking them back to back covers the same ground
     * with no gap. A list short enough to send as one route comes back as one part, and anything
     * under two stops comes back as nothing, since there is no route to walk.
     */
    fun <T> splitForMaps(stops: List<T>): List<List<T>> {
        if (stops.size < 2) return emptyList()

        val parts = mutableListOf<List<T>>()
        var start = 0
        while (start < stops.size - 1) {
            val end = minOf(start + MAX_ROUTE_STOPS, stops.size)
            parts += stops.subList(start, end)
            start = end - 1
        }
        return parts
    }

    /** What Maps should navigate to: the point itself when we have it, else something to search. */
    private fun point(stop: RoutePoint): String =
        if (stop.lat != null && stop.lng != null) "${stop.lat},${stop.lng}" else stop.label

    private fun placeIdParam(name: String, placeId: String): String =
        if (placeId.isNotBlank()) "&$name=${encode(placeId)}" else ""

    /** Percent encoding, with %20 for spaces because Maps handles those more reliably than +. */
    internal fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
}
