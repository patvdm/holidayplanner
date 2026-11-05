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
    fun `a city's own order puts high priority first, each group in dragged order`() {
        val rows = listOf(
            spot(id = "s1", name = "Normal first", sortKey = "G"),
            spot(id = "s2", name = "High late", sortKey = "k", priority = Priority.HIGH),
            spot(id = "s3", name = "Normal second", sortKey = "V"),
            spot(id = "s4", name = "High early", sortKey = "H", priority = Priority.HIGH),
        )

        val names = rows.sortedSpots(SortMode.MANUAL).map { it.spot.name }

        assertEquals(listOf("High early", "High late", "Normal first", "Normal second"), names)
    }

    @Test
    fun `a drop takes its neighbours from its own priority group only`() {
        // As shown: two high spots, then the normal one just dragged to the top of the normals.
        val order = listOf(
            spot(id = "h1", name = "H1", sortKey = "V", priority = Priority.HIGH),
            spot(id = "h2", name = "H2", sortKey = "z", priority = Priority.HIGH),
            spot(id = "n2", name = "N2", sortKey = "t"),
            spot(id = "n1", name = "N1", sortKey = "G"),
        )

        // Not between "z" (a high spot) and "G", which would sort it after N1 again.
        assertEquals(null to "G", order.dropNeighbours("n2"))
        assertEquals("V" to null, order.dropNeighbours("h2"))
        assertEquals(null, order.dropNeighbours("missing"))
    }

    private fun spot(
        id: String,
        name: String,
        sortKey: String,
        priority: Priority = Priority.NORMAL,
    ) = SpotRow(
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
            priority = priority,
            imageUrl = "",
        ),
        linkCount = 0,
        webLinkCount = 0,
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
