package com.pat.app.holidayplanner.data.repo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import com.pat.app.holidayplanner.data.image.PhotoQueue
import com.pat.app.holidayplanner.data.image.PhotoScaler
import com.pat.app.holidayplanner.data.local.AppDatabase
import com.pat.app.holidayplanner.data.prefs.AppPreferences
import com.pat.app.holidayplanner.data.remote.ImageStore
import com.pat.app.holidayplanner.data.remote.StoredImage
import com.pat.app.holidayplanner.data.sync.RoomTransactor
import com.pat.app.holidayplanner.data.sync.SyncScheduler
import com.pat.app.holidayplanner.domain.LinkSource
import com.pat.app.holidayplanner.domain.PictureOrigin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Which picture a spot shows once it has photos: your newest one, unless somebody chose another. */
@RunWith(RobolectricTestRunner::class)
class CoverPictureTest {

    private lateinit var db: AppDatabase
    private lateinit var spots: SpotRepository
    private lateinit var links: LinkRepository
    private lateinit var attachments: AttachmentRepository
    private var clock = 1_000L

    private val noStore = object : ImageStore {
        override suspend fun upload(bytes: ByteArray, mimeType: String, name: String): Result<StoredImage> =
            Result.failure(UnsupportedOperationException())

        override suspend fun remove(url: String): Result<Unit> = Result.success(Unit)

        override fun owns(url: String): Boolean = false
    }

    @Before
    fun open() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val writer = LocalWriter(AppPreferences(context), { clock++ }, SyncScheduler(context))
        val transactor = RoomTransactor(db)
        val lift = CityLift(db.spotDao(), db.cityDao(), writer)
        val countries = CountryRepository(db.countryDao(), db.cascadeDao(), transactor, writer)
        val cities = CityRepository(db.cityDao(), db.cascadeDao(), transactor, writer)
        spots = SpotRepository(db.spotDao(), db.cascadeDao(), transactor, writer, lift)
        links = LinkRepository(db.linkDao(), writer, lift)
        attachments = AttachmentRepository(
            queue = PhotoQueue(context, PhotoScaler(context)),
            store = noStore,
            links = links,
            places = Places(countries, cities, spots),
        )
    }

    @After
    fun close() = db.close()

    private fun picture(spotId: String): String = runBlocking { spots.observeSpot(spotId).first()!!.imageUrl }

    private suspend fun spotWithPhotos(picture: String, origin: PictureOrigin): String {
        val id = spots.add(cityId = "", countryId = "c1", name = "Waag")
        spots.setPicture(id, picture, origin)
        links.add("https://photos.example/old.jpg", LinkSource.PHOTO, AttachmentOwner(spotId = id))
        links.add("https://photos.example/new.jpg", LinkSource.PHOTO, AttachmentOwner(spotId = id))
        return id
    }

    @Test
    fun `your newest photo takes over from a picture the app found`() = runBlocking {
        val id = spotWithPhotos("https://upload.wikimedia.org/waag.jpg", PictureOrigin.AUTO)

        attachments.adoptPhotosAsPictures()

        assertEquals("https://photos.example/new.jpg", picture(id))
    }

    @Test
    fun `a picture somebody chose stays`() = runBlocking {
        val id = spotWithPhotos("https://example.nl/chosen.jpg", PictureOrigin.CHOSEN)

        attachments.adoptPhotosAsPictures()

        assertEquals("https://example.nl/chosen.jpg", picture(id))
    }

    @Test
    fun `a photo made the main picture by hand counts as chosen`() = runBlocking {
        val id = spotWithPhotos("", PictureOrigin.AUTO)
        val old = links.uploadedPhotos().first { it.url.endsWith("old.jpg") }

        attachments.useAsPicture(old)
        attachments.adoptPhotosAsPictures()

        assertEquals("https://photos.example/old.jpg", picture(id))
        assertEquals("CHOSEN", spots.observeSpot(id).first()!!.imageSource)
    }
}
