package com.pat.app.holidayplanner.ui.map

import com.pat.app.holidayplanner.data.local.RouteLinePoint
import com.pat.app.holidayplanner.domain.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Test

class RouteLinesTest {

    private fun stop(
        route: String,
        city: String = "ams",
        made: Long = 1,
        lat: Double = 52.0,
        spot: String = "",
        walked: Boolean = false,
    ) = RouteLinePoint(
        routeId = route, spotId = spot, routeName = "Walk $route", cityId = city, createdAt = made,
        completed = walked, lat = lat, lng = 4.9,
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

    @Test
    fun `a walked route is drawn but takes no colour from the walks ahead`() {
        val lines = listOf(
            stop("done", made = 1, walked = true), stop("done", made = 1, walked = true),
            stop("next", made = 2), stop("next", made = 2),
        ).toRouteLines().associateBy { it.routeId }

        assertEquals(true, lines.getValue("done").walked)
        assertEquals(false, lines.getValue("next").walked)
        assertEquals(0, lines.getValue("next").style)
    }

    @Test
    fun `the first stop starts a route, the last ends it and the rest are along the way`() {
        val badges = listOf(stop("r", spot = "a"), stop("r", spot = "b"), stop("r", spot = "c")).routeBadges()

        assertEquals(RouteRole.START, badges.getValue("a").role)
        assertEquals(RouteRole.STOP, badges.getValue("b").role)
        assertEquals(RouteRole.END, badges.getValue("c").role)
    }

    @Test
    fun `a route of one positioned stop has no start or end`() {
        assertEquals(RouteBadge(RouteRole.STOP, walked = false), listOf(stop("r", spot = "a")).routeBadges()["a"])
    }

    @Test
    fun `a walk ahead outranks a walked one, and a start or end outranks a stop`() {
        val badges = listOf(
            stop("old", spot = "a", walked = true), stop("old", spot = "x", walked = true),
            stop("new", spot = "y"), stop("new", spot = "a"), stop("new", spot = "z"),
            stop("other", spot = "y"), stop("other", spot = "q"),
        ).routeBadges()

        assertEquals(RouteBadge(RouteRole.STOP, walked = false), badges["a"])
        assertEquals(RouteBadge(RouteRole.START, walked = false), badges["y"])
        assertEquals(RouteBadge(RouteRole.END, walked = true), badges["x"])
    }
}
