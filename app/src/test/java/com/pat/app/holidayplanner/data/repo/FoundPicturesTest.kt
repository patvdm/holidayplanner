package com.pat.app.holidayplanner.data.repo

import com.pat.app.holidayplanner.data.local.LinkEntity
import com.pat.app.holidayplanner.data.local.SyncMeta
import com.pat.app.holidayplanner.data.remote.PictureCandidate
import com.pat.app.holidayplanner.data.remote.PictureFrom
import com.pat.app.holidayplanner.data.remote.PictureSource
import com.pat.app.holidayplanner.data.remote.SpotPictureQuery
import com.pat.app.holidayplanner.domain.LinkSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class FoundPicturesTest {

    private val query =
        SpotPictureQuery(name = "Waag", cityName = "Amsterdam", countryName = "Netherlands", isoCode = "NL")

    private class CountingSource(private val answer: Result<List<PictureCandidate>>) : PictureSource {
        var asked = 0
        override suspend fun forPlace(name: String, within: String) = Result.success<String?>(null)
        override suspend fun forSpot(query: SpotPictureQuery) = Result.success<String?>(null)
        override suspend fun candidatesForPlace(name: String, within: String) = answer
        override suspend fun candidatesForSpot(query: SpotPictureQuery): Result<List<PictureCandidate>> {
            asked++
            return answer
        }
    }

    @Test
    fun `only pictures of the place itself are offered, and asked for once`() = runTest {
        val source = CountingSource(
            Result.success(
                listOf(
                    PictureCandidate("https://w.org/waag.jpg", PictureFrom.WIKIPEDIA),
                    PictureCandidate("https://w.org/street.jpg", PictureFrom.COMMONS, metres = 30, named = false),
                ),
            ),
        )
        val found = FoundPictures(source)

        assertEquals(listOf("https://w.org/waag.jpg"), found.forSpot(query).map { it.url })
        found.forSpot(query)
        assertEquals(1, source.asked)
    }

    @Test
    fun `a failed search is tried again next time`() = runTest {
        val source = CountingSource(Result.failure(IllegalStateException("offline")))
        val found = FoundPictures(source)

        assertEquals(emptyList<PictureCandidate>(), found.forSpot(query))
        found.forSpot(query)
        assertEquals(2, source.asked)
    }

    private fun photo(url: String) = LinkEntity(
        meta = SyncMeta.created(url, 0L, "u"),
        spotId = "s",
        cityId = "",
        countryId = "",
        url = url,
        source = LinkSource.PHOTO,
        note = "",
    )

    @Test
    fun `the strip shows the main picture, then your photos, then what was found, each once`() {
        val gallery = galleryOf(
            cover = "https://w.org/waag.jpg?utm_source=x",
            photos = listOf(photo("https://p/new.jpg"), photo("https://p/old.jpg")),
            found = listOf(
                PictureCandidate("https://w.org/waag.jpg", PictureFrom.WIKIPEDIA),
                PictureCandidate("https://w.org/inside.jpg", PictureFrom.COMMONS),
            ),
        )

        assertEquals(
            listOf(
                "https://w.org/waag.jpg?utm_source=x",
                "https://p/new.jpg",
                "https://p/old.jpg",
                "https://w.org/inside.jpg",
            ),
            gallery.map { it.url },
        )
        assertEquals(PictureFrom.WIKIPEDIA, gallery.first().from)
        assertEquals(listOf(true, false, false, false), gallery.map { it.isCover })
    }

    @Test
    fun `a photo that is the main picture leads, and keeps what can be done to it`() {
        val old = photo("https://p/old.jpg")
        val gallery = galleryOf(cover = old.url, photos = listOf(photo("https://p/new.jpg"), old), found = emptyList())

        assertEquals(listOf("https://p/old.jpg", "https://p/new.jpg"), gallery.map { it.url })
        assertEquals(old, gallery.first().photo)
    }

    @Test
    fun `with no picture yet there is nothing before the photos`() {
        val gallery = galleryOf(cover = "none", photos = emptyList(), found = emptyList())

        assertEquals(emptyList<GalleryPicture>(), gallery)
    }
}
