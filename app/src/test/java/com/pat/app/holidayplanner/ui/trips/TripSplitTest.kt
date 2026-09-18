package com.pat.app.holidayplanner.ui.trips

import com.pat.app.holidayplanner.data.local.SyncMeta
import com.pat.app.holidayplanner.data.local.TripEntity
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TripSplitTest {

    private val today = LocalDate.of(2026, 4, 5)

    @Test
    fun `a trip you are still on counts as upcoming`() {
        val split = listOf(trip("Lisbon", "2026-04-01", "2026-04-08")).splitAround(today)

        assertEquals(listOf("Lisbon"), split.upcoming.map { it.name })
        assertTrue(split.past.isEmpty())
    }

    @Test
    fun `a trip that ended yesterday is past`() {
        val split = listOf(trip("Lisbon", "2026-03-28", "2026-04-04")).splitAround(today)

        assertEquals(listOf("Lisbon"), split.past.map { it.name })
    }

    @Test
    fun `a trip ending today is still upcoming`() {
        val split = listOf(trip("Lisbon", "2026-04-01", "2026-04-05")).splitAround(today)

        assertEquals(listOf("Lisbon"), split.upcoming.map { it.name })
    }

    @Test
    fun `upcoming trips are ordered by when they start`() {
        val split = listOf(
            trip("Rome", "2026-09-01", "2026-09-08"),
            trip("Lisbon", "2026-05-01", "2026-05-08"),
        ).splitAround(today)

        assertEquals(listOf("Lisbon", "Rome"), split.upcoming.map { it.name })
    }

    @Test
    fun `past trips show the most recent first`() {
        val split = listOf(
            trip("Old", "2020-01-01", "2020-01-08"),
            trip("Recent", "2026-01-01", "2026-01-08"),
        ).splitAround(today)

        assertEquals(listOf("Recent", "Old"), split.past.map { it.name })
    }

    @Test
    fun `a trip with no dates yet is one you are still planning`() {
        val split = listOf(
            trip("Someday", "", ""),
            trip("Lisbon", "2026-05-01", "2026-05-08"),
        ).splitAround(today)

        assertEquals(listOf("Someday", "Lisbon"), split.upcoming.map { it.name })
    }

    private fun trip(name: String, start: String, end: String) = TripEntity(
        meta = SyncMeta(name, 1, "u1", 1, "u1", 0, deleted = false, dirty = false),
        name = name,
        countryId = "",
        startDate = start,
        endDate = end,
        notes = "",
    )
}
