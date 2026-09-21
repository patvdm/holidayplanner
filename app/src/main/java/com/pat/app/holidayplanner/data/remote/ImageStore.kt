package com.pat.app.holidayplanner.data.remote

/** A stored picture: the address that serves it, and the id needed to take it away again. */
data class StoredImage(val url: String, val fileId: String)

/**
 * Keeps the pictures the sheet cannot: a cell holds an address, not an image.
 *
 * The backend puts them in a Drive folder of its own and hands back an address both phones can
 * fetch without signing in, which is what makes a photo shared on one phone appear on the other.
 */
interface ImageStore {

    /** [bytes] are already scaled down by the caller; a full-size photo would never arrive. */
    suspend fun upload(bytes: ByteArray, mimeType: String, name: String): Result<StoredImage>

    /** Best effort: a picture already gone is not an error. */
    suspend fun remove(fileId: String): Result<Unit>

    /** The file id inside an address this store handed out, or empty for anything else. */
    fun fileIdOf(url: String): String =
        if (url.contains(PICTURE_PATH)) url.substringAfterLast('/').substringBefore('?') else ""

    companion object {
        const val PICTURE_PATH = "googleusercontent.com/d/"
    }
}
