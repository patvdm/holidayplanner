package com.pat.app.holidayplanner.ui.trips

import com.pat.app.holidayplanner.data.local.SyncMeta
import com.pat.app.holidayplanner.data.local.TripItemEntity
import com.pat.app.holidayplanner.data.local.TripItemRow
import com.pat.app.holidayplanner.domain.TripItemType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TripPlanTest {

    private val april1 = LocalDate.of(2026, 4, 1)
    private val april3 = LocalDate.of(2026, 4, 3)

    @Test
    fun `every day of the trip is present even with nothing on it`() {
        val plan = emptyList<TripItemRow>().toPlan(april1, april3)

        assertEquals(listOf(april1, april1.plusDays(1), april3), plan.days.map { it.date })
        assertTrue(plan.days.all { it.entries.isEmpty() })
    }

    @Test
    fun `items land on the day they start`() {
        val plan = listOf(
            item("f1", TripItemType.FLIGHT, "KL1691", start = "2026-04-01T10:15"),
            item("s1", TripItemType.SPOT, "Castle", start = "2026-04-02"),
        ).toPlan(april1, april3)

        assertEquals(listOf("KL1691"), plan.days[0].entries.map { it.title })
        assertEquals(listOf("Castle"), plan.days[1].entries.map { it.title })
        assertTrue(plan.days[2].entries.isEmpty())
    }

    @Test
    fun `an item outside the trip's dates still gets a day rather than vanishing`() {
        val plan = listOf(
            item("s1", TripItemType.SPOT, "Late lunch", start = "2026-04-09"),
        ).toPlan(april1, april3)

        assertEquals(LocalDate.of(2026, 4, 9), plan.days.last().date)
        assertEquals(4, plan.days.size)
    }

    @Test
    fun `a trip with no dates shows only the days something is on`() {
        val plan = listOf(
            item("s1", TripItemType.SPOT, "Castle", start = "2026-04-02"),
        ).toPlan(null, null)

        assertEquals(listOf(LocalDate.of(2026, 4, 2)), plan.days.map { it.date })
    }

    @Test
    fun `within a day timed things come first in time order and the rest follow as planned`() {
        val plan = listOf(
            item("s2", TripItemType.SPOT, "Second stop", start = "2026-04-01", created = 2),
            item("f1", TripItemType.FLIGHT, "Flight", start = "2026-04-01T13:00", created = 3),
            item("s1", TripItemType.SPOT, "First stop", start = "2026-04-01", created = 1),
            item("f0", TripItemType.FLIGHT, "Early flight", start = "2026-04-01T06:00", created = 4),
        ).toPlan(april1, april1)

        assertEquals(
            listOf("Early flight", "Flight", "First stop", "Second stop"),
            plan.days.single().entries.map { it.title },
        )
    }

    @Test
    fun `an item with only an end lands on that day`() {
        val plan = listOf(
            item("s1", TripItemType.SPOT, "Hotel", end = "2026-04-04T10:00"),
        ).toPlan(null, null)

        assertEquals(LocalDate.of(2026, 4, 4), plan.days.single().date)
    }

    @Test
    fun `an item with no date waits to be placed rather than disappearing`() {
        val plan = listOf(
            item("s1", TripItemType.SPOT, "Something we should book"),
            item("f1", TripItemType.FLIGHT, "Flight", start = "2026-04-01T10:15"),
        ).toPlan(april1, april1)

        assertEquals(listOf("Something we should book"), plan.undated.map { it.title })
        assertEquals(1, plan.days.size)
    }

    @Test
    fun `an end before the start still shows the start day`() {
        val plan = emptyList<TripItemRow>().toPlan(april3, april1)

        assertEquals(listOf(april3), plan.days.map { it.date })
    }

    @Test
    fun `a spot row is called after the spot it points at`() {
        val row = item("s1", TripItemType.SPOT, "typed", start = "2026-04-01").copy(spotName = "Mauritshuis")

        assertEquals("Mauritshuis", row.title)
    }

    private fun item(
        id: String,
        type: TripItemType,
        title: String,
        start: String = "",
        end: String = "",
        created: Long = 1,
    ) = TripItemRow(
        item = TripItemEntity(
            meta = SyncMeta(id, created, "u1", created, "u1", 0, deleted = false, dirty = false),
            tripId = "t1",
            type = type,
            title = title,
            origin = "",
            destination = "",
            startLocal = start,
            startTimeZone = "Europe/Amsterdam",
            endLocal = end,
            endTimeZone = "Europe/Amsterdam",
            spotId = "",
            routeId = "",
            bookingRef = "",
            notes = "",
        ),
        spotName = null,
        spotPriority = null,
        spotStatus = null,
        spotType = null,
        spotImageUrl = null,
        routeName = null,
        routeCompleted = null,
        routeCityName = null,
        routeStopCount = 0,
    )
}
