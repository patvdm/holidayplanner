package com.pat.app.holidayplanner.data.sync

import com.pat.app.holidayplanner.data.local.CountryEntity
import com.pat.app.holidayplanner.data.local.HeartEntity
import com.pat.app.holidayplanner.data.local.LinkEntity
import com.pat.app.holidayplanner.data.local.CityEntity
import com.pat.app.holidayplanner.data.local.RouteStopEntity
import com.pat.app.holidayplanner.data.local.SpotEntity
import com.pat.app.holidayplanner.data.local.SyncMeta
import com.pat.app.holidayplanner.data.local.TripEntity
import com.pat.app.holidayplanner.data.local.TripItemEntity
import com.pat.app.holidayplanner.data.local.UserEntity
import com.pat.app.holidayplanner.domain.LinkSource
import com.pat.app.holidayplanner.domain.Priority
import com.pat.app.holidayplanner.domain.SpotStatus
import com.pat.app.holidayplanner.domain.SpotType
import com.pat.app.holidayplanner.domain.TravelMode
import com.pat.app.holidayplanner.domain.TripItemType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Every column has to survive the trip to the sheet and back, which is what catches a name typed
 * differently in the two directions.
 */
class SheetMappingTest {

    private val meta = SyncMeta(
        id = "row-1",
        createdAt = 1_700_000_000_000,
        createdBy = "u1",
        updatedAt = 1_700_000_111_000,
        updatedBy = "u2",
        syncedAt = 0,
        deleted = false,
        dirty = false,
    )

    @Test
    fun `user round trips`() {
        val entity = UserEntity(meta, name = "Pat")
        assertEquals(entity, userFromRow(SheetRow(userToRow(entity))))
    }

    @Test
    fun `country round trips`() {
        val entity = CountryEntity(meta, name = "Netherlands", isoCode = "NL", sortKey = "V", priority = Priority.MUST)
        assertEquals(entity, countryFromRow(SheetRow(countryToRow(entity))))
    }

    @Test
    fun `place round trips`() {
        val entity = CityEntity(
            meta = meta,
            countryId = "c1",
            name = "Den Haag",
            googlePlaceId = "abc",
            lat = 52.0705,
            lng = 4.3007,
            sortKey = "V",
            priority = Priority.LIKE,
        )
        assertEquals(entity, cityFromRow(SheetRow(cityToRow(entity))))
    }

    @Test
    fun `spot round trips`() {
        val entity = SpotEntity(
            meta = meta,
            parentCityId = "p1",
            name = "Mauritshuis",
            type = SpotType.POI,
            googlePlaceId = "xyz",
            lat = 52.0801,
            lng = 4.3145,
            address = "Plein 29, Den Haag",
            mapsUrl = "https://maps.app.goo.gl/AbCdEfGh",
            notes = "Book ahead",
            tags = listOf("museum", "indoor"),
            status = SpotStatus.VISITED,
            sortKey = "G",
            priority = Priority.MUST,
        )
        assertEquals(entity, spotFromRow(SheetRow(spotToRow(entity))))
    }

    @Test
    fun `link round trips`() {
        val entity = LinkEntity(meta, spotId = "", url = "https://x/y", source = LinkSource.INSTAGRAM, note = "reel")
        assertEquals(entity, linkFromRow(SheetRow(linkToRow(entity))))
    }

    @Test
    fun `heart round trips`() {
        val entity = HeartEntity(meta, spotId = "s1", userId = "u1")
        assertEquals(entity, heartFromRow(SheetRow(heartToRow(entity))))
    }

    @Test
    fun `trip round trips`() {
        val entity = TripEntity(
            meta = meta,
            name = "Lisbon",
            startDate = "2026-04-01",
            endDate = "2026-04-08",
            countryIds = listOf("c1", "c2"),
            notes = "with the bikes",
        )
        assertEquals(entity, tripFromRow(SheetRow(tripToRow(entity))))
    }

    @Test
    fun `trip item round trips`() {
        val entity = TripItemEntity(
            meta = meta,
            tripId = "t1",
            type = TripItemType.FLIGHT,
            title = "KL1691",
            origin = "AMS",
            destination = "LIS",
            startLocal = "2026-04-01T10:15",
            startTimeZone = "Europe/Amsterdam",
            endLocal = "2026-04-01T12:40",
            endTimeZone = "Europe/Lisbon",
            address = "",
            googlePlaceId = "",
            lat = null,
            lng = null,
            spotId = "",
            cityId = "city-7",
            travelMode = null,
            bookingRef = "0012AB",
            costMinor = 24_950,
            currency = "EUR",
            docUrl = "https://drive/x",
            notes = "aisle seats",
        )
        assertEquals(entity, tripItemFromRow(SheetRow(tripItemToRow(entity))))
    }

    @Test
    fun `day route keeps its travel mode`() {
        val entity = TripItemEntity(
            meta = meta,
            tripId = "t1",
            type = TripItemType.DAY_ROUTE,
            title = "Old town walk",
            origin = "",
            destination = "",
            startLocal = "2026-04-02T09:00",
            startTimeZone = "Europe/Lisbon",
            endLocal = "",
            endTimeZone = "",
            address = "",
            googlePlaceId = "",
            lat = null,
            lng = null,
            spotId = "",
            cityId = "city-3",
            travelMode = TravelMode.WALKING,
            bookingRef = "",
            costMinor = null,
            currency = "",
            docUrl = "",
            notes = "",
        )
        assertEquals(entity, tripItemFromRow(SheetRow(tripItemToRow(entity))))
    }

    @Test
    fun `route stop round trips`() {
        val entity = RouteStopEntity(meta, tripItemId = "i1", spotId = "s1", sortKey = "V")
        assertEquals(entity, routeStopFromRow(SheetRow(routeStopToRow(entity))))
    }

    @Test
    fun `a booking reference keeps its leading zeros`() {
        val row = tripItemToRow(
            TripItemEntity(
                meta = meta,
                tripId = "t1",
                type = TripItemType.STAY,
                title = "Hotel",
                origin = "",
                destination = "",
                startLocal = "",
                startTimeZone = "",
                endLocal = "",
                endTimeZone = "",
                address = "",
                googlePlaceId = "",
                lat = null,
                lng = null,
                spotId = "",
                cityId = "",
                travelMode = null,
                bookingRef = "000742",
                costMinor = null,
                currency = "",
                docUrl = "",
                notes = "",
            ),
        )
        assertEquals("000742", row["bookingRef"])
        assertEquals("000742", tripItemFromRow(SheetRow(row))?.bookingRef)
    }

    @Test
    fun `an unknown enum value falls back instead of failing`() {
        val row = mapOf("id" to "s1", "updatedAt" to "1", "status" to "TELEPORTED", "priority" to "URGENT")
        val spot = spotFromRow(SheetRow(row))
        assertEquals(SpotStatus.UNVISITED, spot?.status)
        assertEquals(Priority.MAYBE, spot?.priority)
    }

    @Test
    fun `the statuses an older version wrote still read as not visited`() {
        listOf("WANT", "PLANNED", "", "anything").forEach { written ->
            val row = mapOf("id" to "s1", "updatedAt" to "1", "status" to written)
            assertEquals(SpotStatus.UNVISITED, spotFromRow(SheetRow(row))?.status)
        }
        val visited = mapOf("id" to "s1", "updatedAt" to "1", "status" to "visited")
        assertEquals(SpotStatus.VISITED, spotFromRow(SheetRow(visited))?.status)
    }

    @Test
    fun `a spot with no type stays without one`() {
        val row = mapOf("id" to "s1", "updatedAt" to "1", "type" to "")
        assertNull(spotFromRow(SheetRow(row))?.type)
        assertNull(spotFromRow(SheetRow(mapOf("id" to "s1", "updatedAt" to "1")))?.type)
    }

    @Test
    fun `an unknown type is treated as none rather than guessed`() {
        val row = mapOf("id" to "s1", "updatedAt" to "1", "type" to "CASTLE")
        assertNull(spotFromRow(SheetRow(row))?.type)
    }

    @Test
    fun `each type round trips`() {
        SpotType.entries.forEach { type ->
            val row = mapOf("id" to "s1", "updatedAt" to "1", "type" to type.name)
            assertEquals(type, spotFromRow(SheetRow(row))?.type)
        }
    }

    @Test
    fun `blank coordinates stay null rather than becoming zero`() {
        val row = mapOf("id" to "s1", "updatedAt" to "1", "lat" to "", "lng" to "  ")
        val spot = spotFromRow(SheetRow(row))
        assertNull(spot?.lat)
        assertNull(spot?.lng)
    }

    @Test
    fun `a row without an id is not a record`() {
        assertNull(userFromRow(SheetRow(mapOf("name" to "Ghost"))))
    }
}
