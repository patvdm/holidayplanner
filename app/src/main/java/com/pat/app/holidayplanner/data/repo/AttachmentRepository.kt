package com.pat.app.holidayplanner.data.repo

import android.net.Uri
import android.util.Log
import com.pat.app.holidayplanner.data.image.PhotoScaler
import com.pat.app.holidayplanner.data.local.LinkEntity
import com.pat.app.holidayplanner.data.remote.ImageStore
import com.pat.app.holidayplanner.domain.LinkSource
import com.pat.app.holidayplanner.domain.ShareParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val TAG = "AttachmentRepository"

/**
 * What hangs on a place: the links shared to it and the photos taken of it, which are the same
 * thing wearing a different face. Either can belong to a spot or to the city itself.
 *
 * A photo is the one thing in the app that cannot be saved offline, since the picture has to reach
 * the backend before the other phone can show it. Its row is only written once it has.
 */
class AttachmentRepository(
    private val scaler: PhotoScaler,
    private val store: ImageStore,
    private val links: LinkRepository,
) {

    /** Everything hanging on the spots of one city, photos and links alike, keyed by spot. */
    fun observeForSpotsIn(cityId: String): Flow<Map<String, List<LinkEntity>>> =
        links.observeInCity(cityId).map { all -> all.groupBy { it.spotId } }

    /** What hangs on the city itself. */
    fun observeForCity(cityId: String): Flow<List<LinkEntity>> = links.observeForCity(cityId)

    /** A link, against the spot or the city it is about. */
    suspend fun attachLink(url: String, spotId: String = "", cityId: String = ""): String =
        links.add(url = url, source = ShareParser.sourceOf(url), spotId = spotId, cityId = cityId)

    /** Scales the picture, stores it, and hangs it on the spot or the city it is of. */
    suspend fun attach(uri: Uri, spotId: String = "", cityId: String = ""): Result<String> =
        scaler.scale(uri).mapCatching { photo ->
            val of = spotId.ifEmpty { cityId }
            val stored = store.upload(photo.bytes, photo.mimeType, "$of.jpg").getOrThrow()
            links.add(url = stored.url, source = LinkSource.PHOTO, spotId = spotId, cityId = cityId)
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
