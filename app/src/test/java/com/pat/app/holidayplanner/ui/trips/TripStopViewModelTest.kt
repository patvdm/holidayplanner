package com.pat.app.holidayplanner.ui.trips

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import com.pat.app.holidayplanner.data.local.AppDatabase
import com.pat.app.holidayplanner.data.local.TripItemEntity
import com.pat.app.holidayplanner.data.prefs.AppPreferences
import com.pat.app.holidayplanner.data.repo.LocalWriter
import com.pat.app.holidayplanner.data.repo.TripRepository
import com.pat.app.holidayplanner.data.sync.RoomTransactor
import com.pat.app.holidayplanner.data.sync.SyncScheduler
import com.pat.app.holidayplanner.domain.TripItemType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The trip card on a spot: each change is written as it is made, and only to that stop. */
@RunWith(RobolectricTestRunner::class)
class TripStopViewModelTest {

    private lateinit var db: AppDatabase
    private lateinit var trips: TripRepository
    private lateinit var itemId: String

    @Before
    fun open() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val context = ApplicationProvider.getApplicationContext<Context>()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val writer = LocalWriter(AppPreferences(context), { 1_000L }, SyncScheduler(context))
        trips = TripRepository(db.tripDao(), db.tripItemDao(), db.cascadeDao(), RoomTransactor(db), writer)
        itemId = runBlocking {
            val tripId = trips.add("Cape Town", countryId = "", startDate = "2026-12-01", endDate = "2026-12-08")
            trips.addItem(tripId, TripItemType.SPOT, spotId = "lions-head", startLocal = "2026-12-02")
        }
    }

    @After
    fun close() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun item(): TripItemEntity? = runBlocking { trips.findItem(itemId) }

    /** The writes run on the database's own threads, so wait for one to land. */
    private fun waitFor(done: () -> Boolean) = runBlocking {
        withTimeout(WAIT_MS) { while (!done()) delay(10) }
    }

    @Test
    fun `booking and notes are written to the stop, trimmed`() {
        val viewModel = TripStopViewModel(itemId, trips)

        viewModel.setBookingRef(" ABC123 ")
        waitFor { item()?.bookingRef == "ABC123" }
        viewModel.setNotes("Go at sunrise\n")
        waitFor { item()?.notes == "Go at sunrise" }

        assertEquals("ABC123", item()?.bookingRef)
        assertEquals("2026-12-02", item()?.startLocal)
    }

    @Test
    fun `changing the spot says which one it is now`() {
        val viewModel = TripStopViewModel(itemId, trips)
        var shown = ""

        viewModel.setSpot("table-mountain") { shown = it }
        waitFor { shown.isNotEmpty() }

        assertEquals("table-mountain", shown)
        assertEquals("table-mountain", item()?.spotId)
    }

    @Test
    fun `removing takes the stop off the trip`() {
        val viewModel = TripStopViewModel(itemId, trips)
        var removed = false

        viewModel.remove { removed = true }
        waitFor { removed }

        assertTrue(item()?.meta?.deleted == true)
    }

    private companion object {
        const val WAIT_MS = 5_000L
    }
}
