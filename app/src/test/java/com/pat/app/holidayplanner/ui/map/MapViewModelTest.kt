package com.pat.app.holidayplanner.ui.map

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import com.pat.app.holidayplanner.data.local.AppDatabase
import com.pat.app.holidayplanner.data.local.CityEntity
import com.pat.app.holidayplanner.data.local.CountryEntity
import com.pat.app.holidayplanner.data.local.RouteEntity
import com.pat.app.holidayplanner.data.local.RouteStopEntity
import com.pat.app.holidayplanner.data.local.SpotEntity
import com.pat.app.holidayplanner.data.local.SyncMeta
import com.pat.app.holidayplanner.data.local.TripItemEntity
import com.pat.app.holidayplanner.data.prefs.AppPreferences
import com.pat.app.holidayplanner.data.repo.CityLift
import com.pat.app.holidayplanner.data.repo.LinkRepository
import com.pat.app.holidayplanner.data.repo.LocalWriter
import com.pat.app.holidayplanner.data.repo.RouteRepository
import com.pat.app.holidayplanner.data.repo.SpotPinRepository
import com.pat.app.holidayplanner.data.repo.SpotRepository
import com.pat.app.holidayplanner.data.repo.TripRepository
import com.pat.app.holidayplanner.data.sync.RoomTransactor
import com.pat.app.holidayplanner.data.sync.SyncScheduler
import com.pat.app.holidayplanner.domain.MapPinFilter
import com.pat.app.holidayplanner.domain.Priority
import com.pat.app.holidayplanner.domain.SpotStatus
import com.pat.app.holidayplanner.domain.SpotType
import com.pat.app.holidayplanner.domain.TripItemType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The map's own logic over a real in-memory database: what it shows for a scope and a filter. */
@RunWith(RobolectricTestRunner::class)
class MapViewModelTest {

    private lateinit var db: AppDatabase
    private lateinit var prefs: AppPreferences
    private lateinit var sources: MapSources

    @Before
    fun open() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val context = ApplicationProvider.getApplicationContext<Context>()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        prefs = AppPreferences(context)
        val writer = LocalWriter(prefs, { NOW }, SyncScheduler(context))
        val transactor = RoomTransactor(db)
        val lift = CityLift(db.spotDao(), db.cityDao(), writer)
        sources = MapSources(
            pins = SpotPinRepository(db.spotDao()),
            spots = SpotRepository(db.spotDao(), db.cascadeDao(), transactor, writer, lift),
            links = LinkRepository(db.linkDao(), writer, lift),
            routes = RouteRepository(db.routeDao(), db.routeStopDao(), db.cascadeDao(), transactor, writer),
            trips = TripRepository(db.tripDao(), db.tripItemDao(), db.cascadeDao(), transactor, writer),
        )
        runBlocking { seed() }
    }

    @After
    fun close() {
        runBlocking { prefs.setMapPinFilter(MapPinFilter.ALL) }
        db.close()
        Dispatchers.resetMain()
    }

    private fun meta(id: String) = SyncMeta.created(id, at = 1, by = "u")

    private fun spot(id: String, type: SpotType = SpotType.POI) = SpotEntity(
        meta = meta(id), parentCityId = "ams", parentCountryId = "", name = id, type = type,
        googlePlaceId = "", lat = 52.37, lng = 4.89, address = "", mapsUrl = "", notes = "",
        status = SpotStatus.UNVISITED, marks = "", sortKey = "V", priority = Priority.NORMAL, imageUrl = "",
    )

    private fun tripItem(id: String, type: TripItemType, start: String, spotId: String = "", routeId: String = "") =
        TripItemEntity(
            meta = meta(id), tripId = "trip", type = type, title = "", origin = "", destination = "",
            startLocal = start, startTimeZone = "", endLocal = "", endTimeZone = "", spotId = spotId,
            routeId = routeId, bookingRef = "", notes = "",
        )

    private suspend fun seed() {
        db.countryDao().upsert(listOf(CountryEntity(meta("nl"), "Netherlands", "NL", "V", Priority.NORMAL, "")))
        db.cityDao().upsert(
            listOf(CityEntity(meta("ams"), "nl", "Amsterdam", "", null, null, "V", Priority.NORMAL, "", "")),
        )
        db.spotDao().upsert(
            listOf(
                spot("micropia"),
                spot("rosario", SpotType.RESTAURANT),
                spot("lions-head", SpotType.RESTAURANT),
                spot("vondelpark"),
            ),
        )
        db.routeDao().upsert(listOf(RouteEntity(meta("sat"), "ams", "Saturday", false, "")))
        db.routeStopDao().upsert(
            listOf(
                RouteStopEntity(meta("s1"), "sat", "micropia", "V", onMaps = true),
                RouteStopEntity(meta("s2"), "sat", "rosario", "l", onMaps = true),
            ),
        )
        db.tripItemDao().upsert(
            listOf(
                tripItem("fri", TripItemType.SPOT, "2026-11-06T19:00", spotId = "lions-head"),
                tripItem("sat", TripItemType.ROUTE, "2026-11-07T10:00", routeId = "sat"),
            ),
        )
    }

    private fun state(scope: MapScope, until: (MapUiState) -> Boolean): MapUiState = runBlocking {
        withTimeout(TIMEOUT_MS) { MapViewModel(scope, sources, prefs).uiState.first(until) }
    }

    @Test
    fun `a trip day shows its spots and its route, and nothing else`() {
        val saturday = state(MapScope(tripId = "trip", day = "2026-11-07")) { it.hasPins && it.day != null }

        assertEquals(setOf("micropia", "rosario"), saturday.pins.map { it.spotId }.toSet())
        assertEquals(listOf("sat"), saturday.lines.map { it.routeId })
        assertEquals(RouteRole.START, saturday.badges["micropia"]?.role)
    }

    @Test
    fun `the food filter keeps the spot the map was opened for`() {
        runBlocking { prefs.setMapPinFilter(MapPinFilter.FOOD) }

        val opened = state(MapScope(spotId = "micropia")) { it.hasPins && it.filter == MapPinFilter.FOOD }

        assertEquals(setOf("micropia", "rosario", "lions-head"), opened.pins.map { it.spotId }.toSet())
    }

    @Test
    fun `the plain map shows everything and every route`() {
        val all = state(MapScope()) { it.hasPins }

        assertEquals(4, all.pins.size)
        assertEquals(listOf("sat"), all.lines.map { it.routeId })
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
        const val NOW = 1_000L
    }
}
