package com.pat.app.holidayplanner.ui.explore

import com.pat.app.holidayplanner.data.local.CountryEntity
import com.pat.app.holidayplanner.data.local.CountryRow
import com.pat.app.holidayplanner.data.local.SpotEntity
import com.pat.app.holidayplanner.data.local.SpotRow
import com.pat.app.holidayplanner.data.local.SyncMeta
import com.pat.app.holidayplanner.domain.Priority
import com.pat.app.holidayplanner.domain.SortMode
import com.pat.app.holidayplanner.domain.SpotStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class ExploreSortingTest {

    private val netherlands =
        country(id = "c1", name = "Netherlands", sortKey = "G", priority = Priority.MUST, created = 3)
    private val portugal =
        country(id = "c2", name = "Portugal", sortKey = "V", priority = Priority.MAYBE, created = 1)
    private val austria =
        country(id = "c3", name = "Austria", sortKey = "k", priority = Priority.LIKE, created = 2)
    private val countries = listOf(portugal, austria, netherlands)

    @Test
    fun `manual order follows the sort keys`() {
        val names = countries.sortedCountries(SortMode.MANUAL).map { it.country.name }

        assertEquals(listOf("Netherlands", "Portugal", "Austria"), names)
    }

    @Test
    fun `priority order puts must first and keeps manual order inside a level`() {
        val names = countries.sortedCountries(SortMode.PRIORITY).map { it.country.name }

        assertEquals(listOf("Netherlands", "Austria", "Portugal"), names)
    }

    @Test
    fun `recently added shows the newest first`() {
        val names = countries.sortedCountries(SortMode.RECENT).map { it.country.name }

        assertEquals(listOf("Netherlands", "Austria", "Portugal"), names)
    }

    @Test
    fun `a to z ignores case`() {
        val names = countries.sortedCountries(SortMode.ALPHABETICAL).map { it.country.name }

        assertEquals(listOf("Austria", "Netherlands", "Portugal"), names)
    }

    @Test
    fun `rows with the same sort key fall back to the id`() {
        val first = country(id = "a", name = "First", sortKey = "V", priority = Priority.MAYBE, created = 1)
        val second = country(id = "b", name = "Second", sortKey = "V", priority = Priority.MAYBE, created = 1)

        val names = listOf(second, first).sortedCountries(SortMode.MANUAL).map { it.country.name }

        assertEquals(listOf("First", "Second"), names)
    }

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

    private fun country(id: String, name: String, sortKey: String, priority: Priority, created: Long) = CountryRow(
        country = CountryEntity(
            meta = meta(id, created),
            name = name,
            isoCode = "XX",
            sortKey = sortKey,
            priority = priority,
        ),
        cityCount = 0,
        spotCount = 0,
    )

    private fun spot(id: String, name: String, sortKey: String, hearts: Int) = SpotRow(
        spot = SpotEntity(
            meta = meta(id, 1),
            parentCityId = "p1",
            name = name,
            type = null,
            googlePlaceId = "",
            lat = null,
            lng = null,
            address = "",
            mapsUrl = "",
            notes = "",
            tags = emptyList(),
            status = SpotStatus.UNVISITED,
            sortKey = sortKey,
            priority = Priority.MAYBE,
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
