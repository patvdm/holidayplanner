package com.pat.app.holidayplanner.data.repo

import com.pat.app.holidayplanner.domain.PictureOrigin
import android.net.Uri
import android.util.Log
import com.pat.app.holidayplanner.data.image.PhotoQueue
import com.pat.app.holidayplanner.data.local.LinkEntity
import com.pat.app.holidayplanner.data.remote.ImageStore
import com.pat.app.holidayplanner.domain.LinkSource
import com.pat.app.holidayplanner.domain.NO_PICTURE
import com.pat.app.holidayplanner.domain.ShareParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

private const val TAG = "AttachmentRepository"

/** How one attempt at sending a waiting photo went. */
enum class UploadOutcome { DONE, RETRY }

/**
 * What hangs on a place: the links shared to it and the photos taken of it, which are the same
 * thing wearing a different face. Either can belong to a spot, a city or a country.
 *
 * A photo is saved on the phone first and uploaded in the background (see [PhotoQueue]), so adding
 * one never waits on the network. The other phone sees it once the upload is through.
 */
class AttachmentRepository(
    private val queue: PhotoQueue,
    private val store: ImageStore,
    private val links: LinkRepository,
    private val places: Places,
) {

    /** Everything hanging on a country, its cities and their spots, sorted by owner. */
    fun observeInCountry(countryId: String): Flow<CountryAttachments> =
        links.observeInCountry(countryId).map(CountryAttachments::of)

    /** Everywhere in a country a photo or link could be moved to. */
    fun observeTargets(countryId: String): Flow<List<AttachmentTarget>> = places.observeTargets(countryId)

    /** The same, for a screen that only learns its country once something else has loaded. */
    fun observeTargetsOrNone(countryId: String?): Flow<List<AttachmentTarget>> =
        if (countryId.isNullOrEmpty()) flowOf(emptyList()) else observeTargets(countryId)

    /** A link, against the spot, city or country it is about. */
    suspend fun attachLink(url: String, owner: AttachmentOwner): String =
        links.add(url = url, source = ShareParser.sourceOf(url), owner = owner)

    /**
     * Scales the picture, keeps it on the phone, and hangs it on whatever it is of straight away.
     * The upload happens afterwards in the background; this only fails if the picture cannot be read.
     */
    suspend fun attach(uri: Uri, owner: AttachmentOwner): Result<String> =
        queue.stage(uri).map { localUrl ->
            // A place with no picture is about to get one from this photo. Saying "none" for now
            // keeps the automatic picture search from filling the gap while the photo uploads.
            if (places.pictureOf(owner) == "") places.setPicture(owner, NO_PICTURE, PictureOrigin.PHOTO)
            val id = links.add(url = localUrl, source = LinkSource.PHOTO, owner = owner)
            queue.enqueue(id)
            id
        }

    /**
     * Sends one waiting photo and gives its row the real address. A photo deleted before it got
     * there is dropped quietly, including from the store if it arrived just as it was deleted.
     */
    suspend fun finishUpload(linkId: String): UploadOutcome {
        val link = links.find(linkId)?.takeIf { PhotoQueue.isWaiting(it.url) } ?: return UploadOutcome.DONE
        val bytes = if (link.meta.deleted) null else queue.bytesOf(link.url)
        if (bytes == null) {
            dropWaiting(link)
            return UploadOutcome.DONE
        }
        return upload(link, bytes)
    }

    private suspend fun upload(link: LinkEntity, bytes: ByteArray): UploadOutcome {
        val stored = store.upload(bytes, "image/jpeg", "${AttachmentOwner.of(link).fileStem}.jpg")
            .onFailure { Log.w(TAG, "upload of ${link.meta.id} failed: ${it.message}") }
            .getOrNull() ?: return UploadOutcome.RETRY
        // Deleted while it was on its way: take it straight back out of the store.
        if (links.completeUpload(link.meta.id, stored.url)) {
            // Read again, since the photo may have been moved while it was uploading.
            links.find(link.meta.id)?.let { adoptAsPicture(AttachmentOwner.of(it), stored.url) }
        } else {
            store.remove(stored.url)
        }
        queue.discard(link.url)
        return UploadOutcome.DONE
    }

    /**
     * A waiting photo that can never arrive: deleted before it went, or its file gone. A row that
     * points at nothing helps no one, so it goes too.
     */
    private suspend fun dropWaiting(link: LinkEntity) {
        if (!link.meta.deleted) {
            Log.w(TAG, "waiting photo ${link.meta.id} lost its file")
            links.delete(link.meta.id)
        }
        queue.discard(link.url)
    }

    /** Makes [url] the place's main picture, but only if it has none of its own. */
    private suspend fun adoptAsPicture(owner: AttachmentOwner, url: String) {
        if (places.lacksPicture(owner)) places.setPicture(owner, url, PictureOrigin.PHOTO)
    }

    /**
     * Gives every place that has photos but no picture its first photo as the picture. Run at app
     * start, before the automatic picture search, so a place someone photographed is never given
     * a stranger's picture instead.
     */
    suspend fun adoptPhotosAsPictures() {
        links.uploadedPhotos()
            .groupBy(AttachmentOwner::of)
            .forEach { (owner, photos) -> adoptAsPicture(owner, photos.first().url) }
    }

    /** Shows this photo as the main picture of whatever it hangs on, replacing what was there. */
    suspend fun useAsPicture(photo: LinkEntity) {
        if (!photo.source.isPhoto || PhotoQueue.isWaiting(photo.url)) return
        places.setPicture(AttachmentOwner.of(photo), photo.url, PictureOrigin.PHOTO)
    }

    /** Queues again every photo still waiting, in case its upload was lost. Called at app start. */
    suspend fun resumeUploads() {
        links.waitingPhotoIds().forEach(queue::enqueue)
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
        if (PhotoQueue.isWaiting(attachment.url)) {
            queue.discard(attachment.url)
            return
        }
        replacePictureIfGone(attachment)
        store.remove(attachment.url).onFailure { Log.w(TAG, "left ${attachment.url} in the store: ${it.message}") }
    }

    /**
     * A deleted photo that was the place's main picture would leave it pointing at nothing. The
     * next photo of the place takes over; with none left, the automatic search gets another go.
     */
    private suspend fun replacePictureIfGone(photo: LinkEntity) {
        val owner = AttachmentOwner.of(photo)
        if (places.pictureOf(owner) != photo.url) return
        val next = links.uploadedPhotos().firstOrNull { AttachmentOwner.of(it) == owner && it.url != photo.url }
        places.setPicture(owner, next?.url.orEmpty(), PictureOrigin.PHOTO)
    }
}
