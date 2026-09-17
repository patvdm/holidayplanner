package com.pat.app.holidayplanner.ui.trips

import com.pat.app.holidayplanner.data.local.SyncMeta
import com.pat.app.holidayplanner.data.local.TripItemEntity
import com.pat.app.holidayplanner.domain.TripItemType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TripTimelineTest {

    @Test
    fun `items land on the day they start`() {
        val timeline = listOf(
            item("f1", TripItemType.FLIGHT, "KL1691", start = "2026-04-01T10:15"),
            item("a1", TripItemType.ACTIVITY, "Castle", start = "2026-04-02T09:00"),
        ).toTimeline()

        assertEquals(listOf(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 2)), timeline.days.map { it.date })
    }

    @Test
    fun `a stay shows on both the day in and the day out`() {
        val timeline = listOf(
            item("s1", TripItemType.STAY, "Hotel", start = "2026-04-01T15:00", end = "2026-04-04T10:00"),
        ).toTimeline()

        assertEquals(2, timeline.days.size)
        assertEquals(ItemRole.START, timeline.days.first().entries.single().role)
        assertEquals(ItemRole.END, timeline.days.last().entries.single().role)
    }

    @Test
    fun `a one night stay that starts and ends the same day shows once`() {
        val timeline = listOf(
            item("s1", TripItemType.STAY, "Hotel", start = "2026-04-01T15:00", end = "2026-04-01T23:00"),
        ).toTimeline()

        assertEquals(1, timeline.days.size)
        assertEquals(1, timeline.days.single().entries.size)
    }

    @Test
    fun `a flight that lands the next day still shows only once`() {
        val timeline = listOf(
            item("f1", TripItemType.FLIGHT, "Red eye", start = "2026-04-01T23:30", end = "2026-04-02T06:00"),
        ).toTimeline()

        assertEquals(1, timeline.days.size)
        assertEquals(LocalDate.of(2026, 4, 1), timeline.days.single().date)
    }

    @Test
    fun `a day is ordered by the time each card shows`() {
        val timeline = listOf(
            item("f1", TripItemType.FLIGHT, "Flight", start = "2026-04-04T13:00"),
            item("s1", TripItemType.STAY, "Hotel", start = "2026-04-01T15:00", end = "2026-04-04T10:00"),
            item("a1", TripItemType.ACTIVITY, "Lunch", start = "2026-04-04T12:00"),
        ).toTimeline()

        val lastDay = timeline.days.last { it.date == LocalDate.of(2026, 4, 4) }
        assertEquals(listOf("Hotel", "Lunch", "Flight"), lastDay.entries.map { it.item.title })
    }

    @Test
    fun `an item with no times waits to be finished rather than disappearing`() {
        val timeline = listOf(
            item("a1", TripItemType.ACTIVITY, "Something we should book"),
            item("f1", TripItemType.FLIGHT, "Flight", start = "2026-04-01T10:15"),
        ).toTimeline()

        assertEquals(listOf("Something we should book"), timeline.undated.map { it.title })
        assertEquals(1, timeline.days.size)
    }

    @Test
    fun `an item with only an end time lands on that day`() {
        val timeline = listOf(
            item("s1", TripItemType.STAY, "Hotel", end = "2026-04-04T10:00"),
        ).toTimeline()

        assertEquals(LocalDate.of(2026, 4, 4), timeline.days.single().date)
        assertEquals(ItemRole.END, timeline.days.single().entries.single().role)
    }

    @Test
    fun `days come back in order whatever order the items arrived in`() {
        val timeline = listOf(
            item("c", TripItemType.ACTIVITY, "Third", start = "2026-04-09T09:00"),
            item("a", TripItemType.ACTIVITY, "First", start = "2026-04-01T09:00"),
            item("b", TripItemType.ACTIVITY, "Second", start = "2026-04-05T09:00"),
        ).toTimeline()

        assertEquals(listOf("First", "Second", "Third"), timeline.days.map { it.entries.single().item.title })
    }

    @Test
    fun `a trip with nothing in it has nothing to show`() {
        val timeline = emptyList<TripItemEntity>().toTimeline()

        assertTrue(timeline.days.isEmpty())
        assertTrue(timeline.undated.isEmpty())
    }

    private fun item(
        id: String,
        type: TripItemType,
        title: String,
        start: String = "",
        end: String = "",
    ) = TripItemEntity(
        meta = SyncMeta(id, 1, "u1", 1, "u1", 0, deleted = false, dirty = false),
        tripId = "t1",
        type = type,
        title = title,
        origin = "",
        destination = "",
        startLocal = start,
        startTimeZone = "Europe/Amsterdam",
        endLocal = end,
        endTimeZone = "Europe/Amsterdam",
        address = "",
        googlePlaceId = "",
        lat = null,
        lng = null,
        spotId = "",
        travelMode = null,
        bookingRef = "",
        costMinor = null,
        currency = "",
        docUrl = "",
        notes = "",
    )
}
