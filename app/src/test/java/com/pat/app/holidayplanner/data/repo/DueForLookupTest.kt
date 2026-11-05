package com.pat.app.holidayplanner.data.repo

import com.pat.app.holidayplanner.data.local.SpotEntity
import com.pat.app.holidayplanner.data.local.SyncMeta
import com.pat.app.holidayplanner.domain.Priority
import com.pat.app.holidayplanner.domain.SpotStatus
import com.pat.app.holidayplanner.domain.SpotType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class DueForLookupTest {

    private val today = LocalDate.of(2026, 11, 5)

    private fun spot(
        id: String,
        hours: String = "",
        lat: Double? = 52.0,
        placeId: String = "",
        priority: Priority = Priority.NORMAL,
    ) = SpotEntity(
        meta = SyncMeta.created(id, 1, "u"), parentCityId = "ams", parentCountryId = "", name = id,
        type = SpotType.POI, googlePlaceId = placeId, lat = lat, lng = lat?.let { 4.9 }, address = "",
        mapsUrl = "", notes = "", status = SpotStatus.UNVISITED, marks = "", sortKey = "V",
        priority = priority, imageUrl = "", openingHours = hours,
    )

    @Test
    fun `a run skips spots checked this week and spots Google cannot be asked about`() {
        val due = listOf(
            spot("never"),
            spot("this-week", hours = "2026-11-01|none"),
            spot("last-month", hours = "2026-10-01|always"),
            spot("nowhere", lat = null),
            spot("by-id", lat = null, placeId = "ChIJ"),
        ).dueForLookup(today)

        assertEquals(setOf("never", "last-month", "by-id"), due.map { it.meta.id }.toSet())
    }

    @Test
    fun `high priority goes first`() {
        val due = listOf(spot("normal"), spot("high", priority = Priority.HIGH)).dueForLookup(today)

        assertEquals("high", due.first().meta.id)
    }
}
