package com.pat.app.holidayplanner.data.repo

import android.net.Uri
import android.util.Log
import com.pat.app.holidayplanner.data.image.PhotoScaler
import com.pat.app.holidayplanner.data.local.LinkEntity
import com.pat.app.holidayplanner.data.remote.ImageStore
import com.pat.app.holidayplanner.domain.LinkSource
import com.pat.app.holidayplanner.domain.ShareParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

private const val TAG = "AttachmentRepository"

/**
 * What hangs on a place: the links shared to it and the photos taken of it, which are the same
 * thing wearing a different face. Either can belong to a spot, a city or a country.
 *
 * A photo is the one thing in the app that cannot be saved offline, since the picture has to reach
 * the backend before the other phone can show it. Its row is only written once it has.
 */
class AttachmentRepository(
    private val scaler: PhotoScaler,
    private val store: ImageStore,
    private val links: LinkRepository,
    private val countries: CountryRepository,
    private val cities: CityRepository,
    private val spots: SpotRepository,
) {

    /** Everything hanging on a country, its cities and their spots, sorted by owner. */
    fun observeInCountry(countryId: String): Flow<CountryAttachments> =
        links.observeInCountry(countryId).map(CountryAttachments::of)

    /** Everywhere in a country a photo or link could be moved to. */
    fun observeTargets(countryId: String): Flow<List<AttachmentTarget>> = combine(
        countries.observeCountry(countryId),
        cities.observeCities(countryId),
        spots.observeRowsInCountry(countryId, userId = ""),
    ) { country, cityList, spotRows ->
        if (country == null) emptyList() else attachmentTargets(country, cityList, spotRows.map { it.spot })
    }

    /** The same, for a screen that only learns its country once something else has loaded. */
    fun observeTargetsOrNone(countryId: String?): Flow<List<AttachmentTarget>> =
        if (countryId.isNullOrEmpty()) flowOf(emptyList()) else observeTargets(countryId)

    /** A link, against the spot, city or country it is about. */
    suspend fun attachLink(url: String, owner: AttachmentOwner): String =
        links.add(url = url, source = ShareParser.sourceOf(url), owner = owner)

    /** Scales the picture, stores it, and hangs it on whatever it is of. */
    suspend fun attach(uri: Uri, owner: AttachmentOwner): Result<String> =
        scaler.scale(uri).mapCatching { photo ->
            val stored = store.upload(photo.bytes, photo.mimeType, "${owner.fileStem}.jpg").getOrThrow()
            links.add(url = stored.url, source = LinkSource.PHOTO, owner = owner)
        }

    /** Hangs a photo or a link on something else. The picture itself stays where it is stored. */
    suspend fun moveTo(attachment: LinkEntity, owner: AttachmentOwner) {
        if (AttachmentOwner.of(attachment) == owner) return
        links.reassign(attachment.meta.id, owner)
    }

    /**
     * Takes a photo or a link off whatever it hangs on. A photo goes out of the backend's folder
     * too, and the row goes first: if the file cannot be reached, the picture is still gone from
     * both phones, which is what was asked for.
     */
    suspend fun remove(attachment: LinkEntity) {
        links.delete(attachment.meta.id)
        if (!attachment.source.isPhoto) return
        val fileId = store.fileIdOf(attachment.url)
        if (fileId.isEmpty()) return
        store.remove(fileId).onFailure { Log.w(TAG, "left ${attachment.url} in the bin folder: ${it.message}") }
    }
}
