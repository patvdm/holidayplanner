package com.pat.app.holidayplanner.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pat.app.holidayplanner.domain.Priority
import com.pat.app.holidayplanner.domain.SpotStatus
import com.pat.app.holidayplanner.domain.SpotType
import com.pat.app.holidayplanner.domain.TripItemType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The SQL behind the map, the trip day and the sync count, run against a real in-memory database.
 * Room checks these queries compile; this checks they answer the right question.
 */
@RunWith(RobolectricTestRunner::class)
class DaoQueriesTest {

    private lateinit var db: AppDatabase

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun close() = db.close()

    private fun meta(id: String, deleted: Boolean = false, dirty: Boolean = false) =
        SyncMeta.created(id, at = 1, by = "u").copy(deleted = deleted, dirty = dirty)

    private suspend fun country(id: String = "nl", deleted: Boolean = false) = db.countryDao().upsert(
        listOf(CountryEntity(meta(id, deleted), "Netherlands", "NL", "V", Priority.NORMAL, "")),
    )

    private suspend fun city(id: String = "ams", countryId: String = "nl", deleted: Boolean = false) =
        db.cityDao().upsert(
            listOf(CityEntity(meta(id, deleted), countryId, "Amsterdam", "", null, null, "V", Priority.NORMAL, "", "")),
        )

    private suspend fun spot(
        id: String,
        cityId: String = "ams",
        countryId: String = "",
        lat: Double? = 52.37,
        placeId: String = "",
        deleted: Boolean = false,
        dirty: Boolean = false,
    ) = db.spotDao().upsert(
        listOf(
            SpotEntity(
                meta = meta(id, deleted, dirty), parentCityId = cityId, parentCountryId = countryId, name = id,
                type = SpotType.POI, googlePlaceId = placeId, lat = lat, lng = lat?.let { 4.89 }, address = "",
                mapsUrl = "", notes = "", status = SpotStatus.UNVISITED, marks = "", sortKey = "V",
                priority = Priority.NORMAL, imageUrl = "",
            ),
        ),
    )

    private suspend fun route(id: String, deleted: Boolean = false, dirty: Boolean = false) =
        db.routeDao().upsert(listOf(RouteEntity(meta(id, deleted, dirty), "ams", "Walk $id", false, "")))

    private suspend fun stop(id: String, routeId: String, spotId: String, sortKey: String, deleted: Boolean = false) =
        db.routeStopDao().upsert(listOf(RouteStopEntity(meta(id, deleted), routeId, spotId, sortKey, onMaps = true)))

    private suspend fun tripItem(
        id: String,
        tripId: String,
        type: TripItemType,
        routeId: String = "",
        spotId: String = "",
    ) =
        db.tripItemDao().upsert(
            listOf(
                TripItemEntity(
                    meta = meta(id), tripId = tripId, type = type, title = "", origin = "", destination = "",
                    startLocal = "2026-11-07T10:00", startTimeZone = "", endLocal = "", endTimeZone = "",
                    spotId = spotId, routeId = routeId, bookingRef = "", notes = "",
                ),
            ),
        )

    @Test
    fun `the map shows positioned spots in live cities, and a country's own spots`() = runTest {
        country()
        city()
        city(id = "gone", deleted = true)
        spot("micropia")
        spot("no-position", lat = null)
        spot("deleted", deleted = true)
        spot("in-deleted-city", cityId = "gone")
        spot("countryside", cityId = "", countryId = "nl")

        val pins = db.spotDao().observePins().first().map { it.spotId }.toSet()

        assertEquals(setOf("micropia", "countryside"), pins)
    }

    @Test
    fun `route lines run through live, positioned stops in walking order`() = runTest {
        country()
        city()
        spot("a")
        spot("b")
        spot("c")
        spot("nowhere", lat = null)
        route("r")
        stop("s1", "r", "c", sortKey = "t")
        stop("s2", "r", "a", sortKey = "V")
        stop("s3", "r", "b", sortKey = "l")
        stop("s4", "r", "nowhere", sortKey = "x")
        stop("s5", "r", "b", sortKey = "z", deleted = true)

        val points = db.routeStopDao().observeLinePoints().first()

        assertEquals(listOf("a", "b", "c"), points.map { it.spotId })
    }

    @Test
    fun `a deleted route draws no line`() = runTest {
        country()
        city()
        spot("a")
        spot("b")
        route("r", deleted = true)
        stop("s1", "r", "a", "V")
        stop("s2", "r", "b", "l")

        assertEquals(emptyList<RouteLinePoint>(), db.routeStopDao().observeLinePoints().first())
    }

    @Test
    fun `the waiting count includes routes`() = runTest {
        route("r", dirty = true)
        spot("a", dirty = true)

        assertEquals(2, db.syncStateDao().observePendingCount().first())
    }

    @Test
    fun `a trip's stop hours cover only its own routes`() = runTest {
        country()
        city()
        spot("a")
        spot("b")
        route("mine")
        route("other")
        stop("s1", "mine", "a", "V")
        stop("s2", "other", "b", "V")
        tripItem("i1", "trip", TripItemType.ROUTE, routeId = "mine")
        tripItem("i2", "elsewhere", TripItemType.ROUTE, routeId = "other")

        val hours = db.tripItemDao().observeStopHours("trip").first()

        assertEquals(listOf("mine" to "a"), hours.map { it.routeId to it.spotName })
    }

    @Test
    fun `a trip's spots are those on its days and the stops of its routes`() = runTest {
        country()
        city()
        spot("dinner")
        spot("on-route")
        spot("elsewhere")
        route("walk")
        stop("s1", "walk", "on-route", "V")
        tripItem("i1", "trip", TripItemType.ROUTE, routeId = "walk")
        tripItem("i2", "trip", TripItemType.SPOT, spotId = "dinner")
        tripItem("i3", "other", TripItemType.SPOT, spotId = "elsewhere")

        assertEquals(setOf("dinner", "on-route"), db.spotDao().onTrip("trip").map { it.meta.id }.toSet())
    }

    @Test
    fun `a city's spots leave out deleted ones and other cities`() = runTest {
        spot("here")
        spot("gone", deleted = true)
        spot("there", cityId = "rotterdam")

        assertEquals(listOf("here"), db.spotDao().inCity("ams").map { it.meta.id })
    }
}
