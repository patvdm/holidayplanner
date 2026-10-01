package com.pat.app.holidayplanner.ui.explore

import com.pat.app.holidayplanner.data.local.CountryEntity
import com.pat.app.holidayplanner.data.local.SpotEntity
import com.pat.app.holidayplanner.data.local.SpotRow
import com.pat.app.holidayplanner.data.local.SyncMeta
import com.pat.app.holidayplanner.domain.Priority
import com.pat.app.holidayplanner.domain.SpotType
import com.pat.app.holidayplanner.domain.SortMode
import com.pat.app.holidayplanner.domain.SpotStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class ExploreSortingTest {

    @Test
    fun `spots by hearts put the most loved first`() {
        val rows = listOf(
            spot(id = "s1", name = "One", sortKey = "G", hearts = 1),
            spot(id = "s2", name = "Two", sortKey = "V", hearts = 3),
            spot(id = "s3", name = "Three", sortKey = "k", hearts = 3),
        )

        val names = rows.sortedSpots(SortMode.HEARTS).map { it.spot.name }

        assertEquals(listOf("Two", "Three", "One"), names)
    }

    private fun spot(id: String, name: String, sortKey: String, hearts: Int) = SpotRow(
        spot = SpotEntity(
            meta = meta(id, 1),
            parentCityId = "p1",
            parentCountryId = "",
            name = name,
            type = SpotType.POI,
            googlePlaceId = "",
            lat = null,
            lng = null,
            address = "",
            mapsUrl = "",
            notes = "",
            status = SpotStatus.UNVISITED,
            marks = "",
            sortKey = sortKey,
            priority = Priority.NORMAL,
            imageUrl = "",
        ),
        heartCount = hearts,
        linkCount = 0,
        heartedByMe = false,
    )

    private fun meta(id: String, created: Long) = SyncMeta(
        id = id,
        createdAt = created,
        createdBy = "u1",
        updatedAt = created,
        updatedBy = "u1",
        syncedAt = 0,
        deleted = false,
        dirty = false,
    )
}
