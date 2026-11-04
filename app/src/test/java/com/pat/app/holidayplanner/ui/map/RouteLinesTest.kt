package com.pat.app.holidayplanner.ui.map

import com.pat.app.holidayplanner.data.local.RouteLinePoint
import com.pat.app.holidayplanner.domain.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Test

class RouteLinesTest {

    private fun stop(route: String, city: String, made: Long, lat: Double) = RouteLinePoint(
        routeId = route, routeName = "Walk $route", cityId = city, createdAt = made,
        completed = false, lat = lat, lng = 4.9,
    )

    @Test
    fun `stops keep their walking order within a route`() {
        val lines = listOf(stop("r1", "ams", 1, 52.1), stop("r1", "ams", 1, 52.3), stop("r1", "ams", 1, 52.2))
            .toRouteLines()

        assertEquals(listOf(GeoPoint(52.1, 4.9), GeoPoint(52.3, 4.9), GeoPoint(52.2, 4.9)), lines.single().points)
    }

    @Test
    fun `a route with a single positioned stop has no line`() {
        val lines = listOf(stop("r1", "ams", 1, 52.1), stop("r2", "ams", 2, 52.2), stop("r2", "ams", 2, 52.3))
            .toRouteLines()

        assertEquals(listOf("r2"), lines.map { it.routeId })
    }

    @Test
    fun `routes in one city get different styles in the order they were made`() {
        val lines = listOf(
            stop("late", "ams", 20, 52.1), stop("late", "ams", 20, 52.2),
            stop("early", "ams", 10, 52.1), stop("early", "ams", 10, 52.2),
            stop("other", "prg", 30, 50.1), stop("other", "prg", 30, 50.2),
        ).toRouteLines().associate { it.routeId to it.style }

        assertEquals(mapOf("early" to 0, "late" to 1, "other" to 0), lines)
    }

    @Test
    fun `styles run through the colours solid, then dashed, then solid again`() {
        assertEquals(RouteLineStyle(colourIndex = 0, dashed = false), RouteLineStyle.of(0, colours = 5))
        assertEquals(RouteLineStyle(colourIndex = 4, dashed = false), RouteLineStyle.of(4, colours = 5))
        assertEquals(RouteLineStyle(colourIndex = 0, dashed = true), RouteLineStyle.of(5, colours = 5))
        assertEquals(RouteLineStyle(colourIndex = 1, dashed = false), RouteLineStyle.of(11, colours = 5))
    }
}
