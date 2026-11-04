package com.pat.app.holidayplanner.ui.map

import com.pat.app.holidayplanner.data.local.RouteLinePoint
import com.pat.app.holidayplanner.domain.GeoPoint

/**
 * One route as a line on the map: its stops in walking order, and which of the line styles it
 * wears. [style] is a running number within the route's city, so two walks through the same city
 * never look alike; see [RouteLineStyle] for what each number is drawn as.
 */
data class RouteLine(
    val routeId: String,
    val name: String,
    val cityId: String,
    val points: List<GeoPoint>,
    val style: Int,
)

/**
 * The map's lines from the stops of every route still to walk, which arrive route by route in
 * walking order. A route with fewer than two positioned stops has nothing to join and is left out.
 *
 * Styles are handed out per city in the order the routes were made, so a route keeps its colour
 * while others are added after it, and only shifts when an older one in the same city is walked or
 * deleted.
 */
fun List<RouteLinePoint>.toRouteLines(): List<RouteLine> {
    val byRoute = groupBy { it.routeId }.values.filter { it.size >= 2 }
    return byRoute
        .groupBy { it.first().cityId }
        .values
        .flatMap { routesInCity ->
            routesInCity
                .sortedWith(compareBy<List<RouteLinePoint>> { it.first().createdAt }.thenBy { it.first().routeId })
                .mapIndexed { index, stops ->
                    val first = stops.first()
                    RouteLine(
                        routeId = first.routeId,
                        name = first.routeName,
                        cityId = first.cityId,
                        points = stops.map { GeoPoint(it.lat, it.lng) },
                        style = index,
                    )
                }
        }
}

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
