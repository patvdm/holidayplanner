package com.pat.app.holidayplanner.ui.map

import com.pat.app.holidayplanner.data.local.RouteLinePoint
import com.pat.app.holidayplanner.domain.GeoPoint

/**
 * One route as a line on the map: its stops in walking order, and which of the line styles it
 * wears. [style] is a running number among the routes still to walk in the route's city, so two
 * walks through the same city never look alike; see [RouteLineStyle] for what each number is drawn
 * as. A [walked] route is drawn thin and grey instead, and takes no style from the others.
 */
data class RouteLine(
    val routeId: String,
    val name: String,
    val cityId: String,
    val points: List<GeoPoint>,
    val style: Int,
    val walked: Boolean = false,
)

/**
 * The map's lines from the stops of every route, which arrive route by route in walking order. A
 * route with fewer than two positioned stops has nothing to join and is left out.
 *
 * Styles are handed out per city in the order the routes were made, counting only the routes still
 * to walk, so a route keeps its colour while others are added after it, and only shifts when an
 * older one in the same city is walked or deleted.
 */
fun List<RouteLinePoint>.toRouteLines(): List<RouteLine> {
    val byRoute = groupBy { it.routeId }.values.filter { it.size >= 2 }
    return byRoute
        .groupBy { it.first().cityId }
        .values
        .flatMap { routesInCity ->
            var nextStyle = 0
            routesInCity
                .sortedWith(compareBy<List<RouteLinePoint>> { it.first().createdAt }.thenBy { it.first().routeId })
                .map { stops ->
                    val first = stops.first()
                    RouteLine(
                        routeId = first.routeId,
                        name = first.routeName,
                        cityId = first.cityId,
                        points = stops.map { GeoPoint(it.lat, it.lng) },
                        style = if (first.completed) 0 else nextStyle++,
                        walked = first.completed,
                    )
                }
        }
}

/** Where a spot falls on a route: where the walk sets off, somewhere along it, or where it ends. */
enum class RouteRole { START, STOP, END }

/** The small route sign on a pin: where on the walk the spot is, and whether that walk is done. */
data class RouteBadge(val role: RouteRole, val walked: Boolean)

/**
 * Each spot's route sign, by spot id. A spot on several routes shows the one that matters most: a
 * walk still to do over a walked one, then its start, then its end, then a stop along the way. A
 * route with a single positioned stop has no start or end to speak of, so that stop is plain.
 */
fun List<RouteLinePoint>.routeBadges(): Map<String, RouteBadge> =
    groupBy { it.routeId }.values
        .flatMap { stops ->
            stops.mapIndexed { index, stop ->
                val role = when {
                    stops.size < 2 -> RouteRole.STOP
                    index == 0 -> RouteRole.START
                    index == stops.lastIndex -> RouteRole.END
                    else -> RouteRole.STOP
                }
                stop.spotId to RouteBadge(role, walked = stop.completed)
            }
        }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, badges) -> badges.minWith(badgeImportance) }

private val badgeImportance = compareBy<RouteBadge>({ it.walked }, {
    when (it.role) {
        RouteRole.START -> 0
        RouteRole.END -> 1
        RouteRole.STOP -> 2
    }
})

/**
 * How a style number is drawn: one of [colours] colours, solid the first time round and dashed the
 * second, which gives twice as many looks before two walks in one city share one.
 */
data class RouteLineStyle(val colourIndex: Int, val dashed: Boolean) {
    companion object {
        fun of(style: Int, colours: Int): RouteLineStyle =
            RouteLineStyle(colourIndex = style % colours, dashed = (style / colours) % 2 == 1)
    }
}
