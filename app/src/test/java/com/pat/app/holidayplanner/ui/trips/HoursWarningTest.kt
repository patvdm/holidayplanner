package com.pat.app.holidayplanner.ui.trips

import com.pat.app.holidayplanner.data.local.RouteStopHours
import com.pat.app.holidayplanner.data.local.SyncMeta
import com.pat.app.holidayplanner.data.local.TripItemEntity
import com.pat.app.holidayplanner.data.local.TripItemRow
import com.pat.app.holidayplanner.domain.TripItemType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek

class HoursWarningTest {

    /** Opens six till late on Friday and Saturday (7 Nov 2026 is a Saturday), shut the rest of the week. */
    private val dinnerOnly = "2026-11-05|51800-60100 61800-70100"

    private fun row(type: TripItemType, start: String, hours: String? = null, route: String = "") = TripItemRow(
        item = TripItemEntity(
            meta = SyncMeta.created("i", 1, "u"), tripId = "t", type = type, title = "", origin = "",
            destination = "", startLocal = start, startTimeZone = "", endLocal = "", endTimeZone = "",
            spotId = "s", routeId = route, bookingRef = "", notes = "",
        ),
        spotName = "Cabron", spotPriority = null, spotStatus = null, spotType = null, spotMarks = null,
        spotImageUrl = null, spotOpeningHours = hours, routeName = null, routeCompleted = null,
        routeCityName = null, routeStopCount = 0,
    )

    @Test
    fun `a spot planned before it opens warns, with that day's hours`() {
        val warning = hoursWarning(row(TripItemType.SPOT, "2026-11-07T17:00", dinnerOnly), emptyMap())

        assertEquals("17:00", (warning as HoursWarning.ClosedAt).time)
        assertEquals(1, warning.thatDay.size)
    }

    @Test
    fun `a spot planned while open does not warn`() {
        assertNull(hoursWarning(row(TripItemType.SPOT, "2026-11-07T19:00", dinnerOnly), emptyMap()))
    }

    @Test
    fun `a spot on a day it never opens warns whatever the time, or with none`() {
        assertEquals(
            HoursWarning.ClosedThatDay(DayOfWeek.MONDAY),
            hoursWarning(row(TripItemType.SPOT, "2026-11-09", dinnerOnly), emptyMap()),
        )
    }

    @Test
    fun `unknown or unasked hours never warn`() {
        assertNull(hoursWarning(row(TripItemType.SPOT, "2026-11-09T12:00", "2026-11-05|none"), emptyMap()))
        assertNull(hoursWarning(row(TripItemType.SPOT, "2026-11-09T12:00", null), emptyMap()))
    }

    @Test
    fun `a route names the stops shut all that day`() {
        val stops = mapOf(
            "r" to listOf(
                RouteStopHours("r", "Cabron", dinnerOnly),
                RouteStopHours("r", "Park", "2026-11-05|always"),
                RouteStopHours("r", "Unknown", ""),
            ),
        )

        assertEquals(
            HoursWarning.StopsClosed(DayOfWeek.MONDAY, listOf("Cabron")),
            hoursWarning(row(TripItemType.ROUTE, "2026-11-09T10:00", route = "r"), stops),
        )
        assertNull(hoursWarning(row(TripItemType.ROUTE, "2026-11-07T10:00", route = "r"), stops))
    }
}
