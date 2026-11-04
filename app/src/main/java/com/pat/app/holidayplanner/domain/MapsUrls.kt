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

    /**
     * The destination and the nine waypoints Maps allows before it. The origin is not one of them:
     * it is the phone's own location, which Maps fills in for us.
     */
    const val MAX_ROUTE_STOPS = 10

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
     * Directions through every stop in order, or null when there are no stops at all.
     *
     * The origin is left out on purpose. Maps then starts from where the phone is, so the walk
     * begins where you are standing instead of at a stop you may already have passed. With
     * [fromHere] off it starts at the first stop instead, for looking a day over before you set
     * out, or from home, where your own location says nothing useful about the walk. That leaves
     * the destination and the nine waypoints Maps allows for the stops themselves, so a longer
     * list has to be split first ([splitForMaps]). The destination's place id is sent when we have
     * it, but the waypoint ids are all or nothing: Maps matches them to the waypoints by position,
     * so a partial list would pin the wrong stops.
     */
    fun route(
        stops: List<RoutePoint>,
        travelMode: TravelMode = TravelMode.WALKING,
        fromHere: Boolean = true,
        navigate: Boolean = false,
    ): String? {
        if (stops.isEmpty()) return null
        // Without your location the walk has to start at a stop, and one stop cannot be both ends.
        if (!fromHere && stops.size < 2) return null

        val origin = if (fromHere) null else stops.first()
        val destination = stops.last()
        val waypoints = if (fromHere) stops.dropLast(1) else stops.subList(1, stops.size - 1)

        return buildString {
            append(DIR_BASE)
            if (origin != null) {
                append("&origin=").append(encode(point(origin)))
                append(placeIdParam("origin_place_id", origin.googlePlaceId))
            }
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
            // Straight into turn-by-turn rather than the overview, for setting off now.
            if (navigate) append("&dir_action=navigate")
        }
    }

    /**
     * Directions to one place. That is simply a route of one stop, and a route of one stop always
     * builds, so the empty string here is a case that cannot happen.
     */
    fun directionsTo(stop: RoutePoint, travelMode: TravelMode = TravelMode.WALKING): String =
        route(listOf(stop), travelMode).orEmpty()

    /**
     * Turn-by-turn navigation to one place, from wherever the phone is. Unlike a route this names
     * no travel mode, so Maps uses whatever you usually travel by: a spot across the country is not
     * a walk, and Maps knows better than we do how you are getting there.
     */
    fun navigateTo(stop: RoutePoint): String = buildString {
        append(DIR_BASE)
        append("&destination=").append(encode(point(stop)))
        append(placeIdParam("destination_place_id", stop.googlePlaceId))
        append("&dir_action=navigate")
    }

    /**
     * Cuts a list of stops into routes Maps will accept.
     *
     * The parts do not overlap. Each one starts from wherever the phone is, and by the time you
     * open part two you are standing at the end of part one, so repeating that stop would only
     * send you to where you already are.
     */
    fun <T> splitForMaps(stops: List<T>): List<List<T>> = stops.chunked(MAX_ROUTE_STOPS)

    /** What Maps should navigate to: the point itself when we have it, else something to search. */
    private fun point(stop: RoutePoint): String =
        if (stop.lat != null && stop.lng != null) "${stop.lat},${stop.lng}" else stop.label

    private fun placeIdParam(name: String, placeId: String): String =
        if (placeId.isNotBlank()) "&$name=${encode(placeId)}" else ""

    /** Percent encoding, with %20 for spaces because Maps handles those more reliably than +. */
    internal fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
}
