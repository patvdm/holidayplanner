package com.pat.app.holidayplanner.data.remote

/** A stored picture: the address that serves it, and the id the store knows it by. */
data class StoredImage(val url: String, val fileId: String)

/**
 * Keeps the pictures the sheet cannot: a cell holds an address, not an image.
 *
 * Every store hands back an address both phones can fetch without signing in, which is what makes
 * a photo shared on one phone appear on the other.
 */
interface ImageStore {

    /** False when this store has not been set up, so another one has to be used. */
    val isConfigured: Boolean get() = true

    /** [bytes] are already scaled down by the caller; a full-size photo would never arrive. */
    suspend fun upload(bytes: ByteArray, mimeType: String, name: String): Result<StoredImage>

    /** Best effort, by the address the store handed out: a picture already gone is not an error. */
    suspend fun remove(url: String): Result<Unit>

    /** True for an address this store handed out. */
    fun owns(url: String): Boolean
}

/**
 * Stores new photos in [primary] when it is set up, and in [fallback] otherwise. Removing goes to
 * whichever store the address belongs to, so photos saved before the switch still come away.
 */
class RoutedImageStore(
    private val primary: ImageStore,
    private val fallback: ImageStore,
) : ImageStore {

    override suspend fun upload(bytes: ByteArray, mimeType: String, name: String): Result<StoredImage> =
        (if (primary.isConfigured) primary else fallback).upload(bytes, mimeType, name)

    override suspend fun remove(url: String): Result<Unit> = when {
        primary.owns(url) -> primary.remove(url)
        fallback.owns(url) -> fallback.remove(url)
        else -> Result.success(Unit)
    }

    override fun owns(url: String): Boolean = primary.owns(url) || fallback.owns(url)
}
