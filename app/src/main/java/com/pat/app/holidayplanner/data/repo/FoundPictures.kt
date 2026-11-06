package com.pat.app.holidayplanner.data.repo

import com.pat.app.holidayplanner.data.local.LinkEntity
import com.pat.app.holidayplanner.data.local.SpotContext
import com.pat.app.holidayplanner.data.local.SpotEntity
import com.pat.app.holidayplanner.data.remote.PictureCandidate
import com.pat.app.holidayplanner.data.remote.PictureFrom
import com.pat.app.holidayplanner.data.remote.PictureSource
import com.pat.app.holidayplanner.data.remote.SpotPictureQuery
import com.pat.app.holidayplanner.domain.LinkSource
import com.pat.app.holidayplanner.domain.pictureUrlOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** How many found pictures sit beside a spot's own: enough to get a feel for it, few enough to load fast. */
private const val MAX_FOUND = 6

/** Spots whose found pictures are kept while the app runs, so going back and forth costs nothing. */
private const val REMEMBERED_SPOTS = 40
private const val LOAD_FACTOR = 0.75f

/**
 * Pictures of a spot found on Wikipedia, Commons and its own website, shown beside its photos.
 * They are only looked at, never stored or synced: the spot's main picture is the one that is kept.
 * Only pictures of the place itself are offered, not whatever happens to stand close to it.
 */
class FoundPictures(private val source: PictureSource) {

    private val lock = Mutex()
    private val remembered = object : LinkedHashMap<SpotPictureQuery, List<PictureCandidate>>(
        REMEMBERED_SPOTS,
        LOAD_FACTOR,
        true,
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<SpotPictureQuery, List<PictureCandidate>>?) =
            size > REMEMBERED_SPOTS
    }

    /** Empty when nothing is found or the search fails; a failure is not remembered, so it is tried again. */
    suspend fun forSpot(query: SpotPictureQuery): List<PictureCandidate> {
        if (query.name.isBlank()) return emptyList()
        lock.withLock { remembered[query] }?.let { return it }
        val found = source.candidatesForSpot(query).getOrNull()?.filter { it.named }?.take(MAX_FOUND)
        if (found != null) lock.withLock { remembered[query] = found }
        return found.orEmpty()
    }
}

/** What the picture search needs to know about a spot: its name, where it is, and its own websites. */
fun spotPictureQuery(spot: SpotEntity?, context: SpotContext?, links: List<LinkEntity>): SpotPictureQuery =
    SpotPictureQuery(
        name = spot?.name.orEmpty(),
        cityName = context?.cityName.orEmpty(),
        countryName = context?.countryName.orEmpty(),
        isoCode = context?.isoCode.orEmpty(),
        lat = spot?.lat,
        lng = spot?.lng,
        websites = links.filter { it.source == LinkSource.WEB }.map { it.url },
    )

/**
 * One picture in a spot's strip. [photo] is set for a photo of your own, which can be moved or
 * removed; [from] says where a found one came from.
 */
data class GalleryPicture(
    val url: String,
    val isCover: Boolean,
    val photo: LinkEntity? = null,
    val from: PictureFrom? = null,
)

/**
 * A spot's pictures in the order the strip shows them: the main picture, then your own photos
 * newest first, then the ones found for it. A picture found twice is shown once.
 */
fun galleryOf(cover: String, photos: List<LinkEntity>, found: List<PictureCandidate>): List<GalleryPicture> {
    val coverUrl = cover.pictureUrlOrNull()
    val own = photos.map { GalleryPicture(it.url, isCover = it.url == coverUrl, photo = it) }
    val first = own.firstOrNull { it.isCover } ?: coverUrl?.let { url ->
        GalleryPicture(url, isCover = true, from = found.firstOrNull { it.url.sameAs(url) }?.from)
    }
    val rest = own.filterNot { it.isCover } + found.map { GalleryPicture(it.url, isCover = false, from = it.from) }
    return (listOfNotNull(first) + rest).distinctBy { it.url.substringBefore('?') }
}

private fun String.sameAs(other: String) = substringBefore('?') == other.substringBefore('?')
