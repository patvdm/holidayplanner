package com.pat.app.holidayplanner.ui.map

import com.pat.app.holidayplanner.data.local.SyncMeta
import com.pat.app.holidayplanner.data.local.TripItemEntity
import com.pat.app.holidayplanner.domain.TripItemType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class DayPlanTest {

    private fun item(type: TripItemType, start: String, spot: String = "", route: String = "", end: String = "") =
        TripItemEntity(
            meta = SyncMeta.created("i-$spot$route$start", 1, "u"),
            tripId = "t", type = type, title = "", origin = "", destination = "",
            startLocal = start, startTimeZone = "", endLocal = end, endTimeZone = "",
            spotId = spot, routeId = route, bookingRef = "", notes = "",
        )

    @Test
    fun `a day takes its spots and routes and nothing from other days`() {
        val plan = listOf(
            item(TripItemType.SPOT, "2026-11-06T19:00", spot = "lions-head"),
            item(TripItemType.ROUTE, "2026-11-07T10:00", route = "saturday"),
            item(TripItemType.SPOT, "2026-11-07", spot = "park"),
            item(TripItemType.FLIGHT, "2026-11-07T08:00"),
        ).dayPlan(LocalDate.of(2026, 11, 7))

        assertEquals(setOf("park"), plan.spotIds)
        assertEquals(setOf("saturday"), plan.routeIds)
    }

    @Test
    fun `an item with only an end date is filed under it`() {
        val plan = listOf(item(TripItemType.SPOT, start = "", end = "2026-11-08", spot = "late"))
            .dayPlan(LocalDate.of(2026, 11, 8))

        assertEquals(setOf("late"), plan.spotIds)
    }
}
