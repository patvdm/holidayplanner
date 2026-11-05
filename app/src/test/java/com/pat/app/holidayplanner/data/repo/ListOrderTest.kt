package com.pat.app.holidayplanner.data.repo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import com.pat.app.holidayplanner.data.local.AppDatabase
import com.pat.app.holidayplanner.data.prefs.AppPreferences
import com.pat.app.holidayplanner.data.sync.RoomTransactor
import com.pat.app.holidayplanner.data.sync.SyncScheduler
import com.pat.app.holidayplanner.domain.LinkSource
import com.pat.app.holidayplanner.domain.PictureOrigin
import com.pat.app.holidayplanner.domain.Priority
import com.pat.app.holidayplanner.domain.SpotStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The order of countries, cities and spots, as the repositories write it: what is new goes to the
 * top of its priority group, a star moves a thing to the top of the other group, and a city rises
 * when one of its spots is added or edited by hand. Nothing else moves anything.
 */
@RunWith(RobolectricTestRunner::class)
class ListOrderTest {

    private lateinit var db: AppDatabase
    private lateinit var countries: CountryRepository
    private lateinit var cities: CityRepository
    private lateinit var spots: SpotRepository
    private lateinit var links: LinkRepository

    @Before
    fun open() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val writer = LocalWriter(AppPreferences(context), { NOW }, SyncScheduler(context))
        val transactor = RoomTransactor(db)
        val lift = CityLift(db.spotDao(), db.cityDao(), writer)
        countries = CountryRepository(db.countryDao(), db.cascadeDao(), transactor, writer)
        cities = CityRepository(db.cityDao(), db.cascadeDao(), transactor, writer)
        spots = SpotRepository(db.spotDao(), db.cascadeDao(), transactor, writer, lift)
        links = LinkRepository(db.linkDao(), writer, lift)
    }

    @After
    fun close() = db.close()

    /** Ids in the order the screens show them: priority first, then by sort key. */
    private fun spotOrder(cityId: String): List<String> = runBlocking {
        db.spotDao().inCity(cityId)
            .sortedWith(compareBy({ it.priority.ordinal }, { it.sortKey }, { it.meta.id }))
            .map { it.name }
    }

    private fun cityOrder(countryId: String): List<String> = runBlocking {
        db.cityDao().observeByCountry(countryId).first()
            .sortedWith(compareBy({ it.priority.ordinal }, { it.sortKey }, { it.meta.id }))
            .map { it.name }
    }

    private fun countryOrder(): List<String> = runBlocking {
        db.countryDao().observeAll().first()
            .sortedWith(compareBy({ it.priority.ordinal }, { it.sortKey }, { it.meta.id }))
            .map { it.name }
    }

    @Test
    fun `a new country goes to the top, and a starred one to the top of the stars`() = runBlocking {
        countries.add("Japan", "JP")
        val nl = countries.add("Netherlands", "NL")
        countries.add("Portugal", "PT")
        assertEquals(listOf("Portugal", "Netherlands", "Japan"), countryOrder())

        countries.togglePriority(nl)
        countries.rename(nl, "Holland")

        assertEquals(listOf("Holland", "Portugal", "Japan"), countryOrder())
    }

    @Test
    fun `a new spot goes to the top of its city, and editing one does not move it`() = runBlocking {
        val nl = countries.add("Netherlands", "NL")
        val ams = cities.add(nl, "Amsterdam")
        val micropia = spots.add(ams, "Micropia")
        spots.add(ams, "Rosario")
        spots.setNotes(micropia, "Book ahead")
        spots.setStatus(micropia, SpotStatus.VISITED)

        assertEquals(listOf("Rosario", "Micropia"), spotOrder(ams))
    }

    @Test
    fun `a starred spot goes to the top of the starred ones`() = runBlocking {
        val nl = countries.add("Netherlands", "NL")
        val ams = cities.add(nl, "Amsterdam")
        val early = spots.add(ams, "Early star")
        val late = spots.add(ams, "Late star")
        spots.add(ams, "Plain")
        spots.setPriority(early, Priority.HIGH)
        spots.setPriority(late, Priority.HIGH)

        assertEquals(listOf("Late star", "Early star", "Plain"), spotOrder(ams))
    }

    @Test
    fun `a city rises when one of its spots is added, edited or given a link`() = runBlocking {
        val nl = countries.add("Netherlands", "NL")
        val ams = cities.add(nl, "Amsterdam")
        val rdam = cities.add(nl, "Rotterdam")
        cities.add(nl, "Utrecht")
        assertEquals(listOf("Utrecht", "Rotterdam", "Amsterdam"), cityOrder(nl))

        val micropia = spots.add(ams, "Micropia")
        assertEquals(listOf("Amsterdam", "Utrecht", "Rotterdam"), cityOrder(nl))

        val kubus = spots.add(rdam, "Kubuswoningen")
        spots.rename(micropia, "Micropia museum")
        assertEquals(listOf("Amsterdam", "Rotterdam", "Utrecht"), cityOrder(nl))

        links.add("https://example.com", LinkSource.WEB, AttachmentOwner(spotId = kubus))
        assertEquals(listOf("Rotterdam", "Amsterdam", "Utrecht"), cityOrder(nl))
    }

    @Test
    fun `ticking a spot visited or the app filling in a picture leaves its city where it is`() = runBlocking {
        val nl = countries.add("Netherlands", "NL")
        val ams = cities.add(nl, "Amsterdam")
        val micropia = spots.add(ams, "Micropia")
        cities.add(nl, "Rotterdam")
        assertEquals(listOf("Rotterdam", "Amsterdam"), cityOrder(nl))

        spots.setStatus(micropia, SpotStatus.VISITED)
        spots.setPicture(micropia, "https://example.com/p.jpg", PictureOrigin.AUTO)
        spots.setOpeningHours(micropia, "2026-11-05|none")

        assertEquals(listOf("Rotterdam", "Amsterdam"), cityOrder(nl))
    }

    @Test
    fun `a starred city stays above the rest, however much happens in the others`() = runBlocking {
        val nl = countries.add("Netherlands", "NL")
        val ams = cities.add(nl, "Amsterdam")
        val rdam = cities.add(nl, "Rotterdam")
        cities.togglePriority(ams)
        spots.add(rdam, "Kubuswoningen")

        assertEquals(listOf("Amsterdam", "Rotterdam"), cityOrder(nl))
    }

    private companion object {
        const val NOW = 1_000L
    }
}
