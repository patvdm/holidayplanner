package com.pat.app.holidayplanner.data.repo

import android.net.Uri
import android.util.Log
import com.pat.app.holidayplanner.data.image.PhotoScaler
import com.pat.app.holidayplanner.data.local.LinkEntity
import com.pat.app.holidayplanner.data.remote.ImageStore
import com.pat.app.holidayplanner.domain.LinkSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val TAG = "PhotoRepository"

/**
 * Photos taken of a spot, kept beside its links because that is what they are: one more reference
 * to the place.
 *
 * Unlike everything else in the app this cannot be done offline, since the picture itself has to
 * reach the backend before either phone can show it. The row is only written once it has.
 */
class PhotoRepository(
    private val scaler: PhotoScaler,
    private val store: ImageStore,
    private val links: LinkRepository,
) {

    /** Everything hanging on the spots of one city, photos and links alike, keyed by spot. */
    fun observeByCity(cityId: String): Flow<Map<String, List<LinkEntity>>> =
        links.observeInCity(cityId).map { all -> all.groupBy { it.spotId } }

    /** Scales the picture, stores it, and hangs it on the spot. */
    suspend fun attach(spotId: String, uri: Uri): Result<String> =
        scaler.scale(uri).mapCatching { photo ->
            val stored = store.upload(photo.bytes, photo.mimeType, "spot-$spotId.jpg").getOrThrow()
            links.add(url = stored.url, source = LinkSource.PHOTO, spotId = spotId)
        }

    /**
     * Takes a photo off a spot and out of the backend's folder. The row goes first: if the file
     * cannot be reached, the picture is still gone from both phones, which is what was asked for.
     */
    suspend fun remove(photo: LinkEntity) {
        links.delete(photo.meta.id)
        val fileId = store.fileIdOf(photo.url)
        if (fileId.isEmpty()) return
        store.remove(fileId).onFailure { Log.w(TAG, "left ${photo.url} in the bin folder: ${it.message}") }
    }
}
