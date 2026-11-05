package com.pat.app.holidayplanner.data.image

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkRequest
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.pat.app.holidayplanner.domain.newId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

private const val FOLDER = "pending-photos"
private const val FILE_SCHEME = "file:"

/**
 * Photos waiting to go up. Picking one scales it and keeps the result on the phone at once, so the
 * photo shows straight away and the screen or share sheet never waits on the network. A
 * [PhotoUploadWorker] then sends it, retrying by itself until it gets through.
 *
 * While a photo waits, its link row points at the local file (a `file:` address). Such a row is
 * never pushed, so the other phone only ever sees a photo that has arrived.
 */
class PhotoQueue(
    context: Context,
    private val scaler: PhotoScaler,
) {

    private val folder = File(context.filesDir, FOLDER)
    private val workManager = WorkManager.getInstance(context.applicationContext)

    /**
     * Scales the picture and keeps it on the phone. Returns its local address. This has to happen
     * while the app that shared the picture still lets us read it, which is why it is not deferred.
     */
    suspend fun stage(uri: Uri): Result<String> = scaler.scale(uri).mapCatching { photo ->
        withContext(Dispatchers.IO) {
            folder.mkdirs()
            val file = File(folder, "${newId()}.jpg")
            file.writeBytes(photo.bytes)
            Uri.fromFile(file).toString()
        }
    }

    /** Sends the photo of link [linkId] when there is a connection. Asking twice changes nothing. */
    fun enqueue(linkId: String) {
        val request = OneTimeWorkRequestBuilder<PhotoUploadWorker>()
            .setInputData(workDataOf(PhotoUploadWorker.LINK_ID to linkId))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
            .build()
        workManager.enqueueUniqueWork("photo-$linkId", ExistingWorkPolicy.KEEP, request)
    }

    /** The bytes kept for a waiting photo, or null if the file has gone. */
    suspend fun bytesOf(url: String): ByteArray? = withContext(Dispatchers.IO) {
        fileOf(url)?.takeIf { it.exists() }?.readBytes()
    }

    /** Forgets a waiting photo's file, once it is uploaded or no longer wanted. */
    suspend fun discard(url: String) {
        withContext(Dispatchers.IO) { fileOf(url)?.delete() }
    }

    /** Only files in our own folder, whatever the address claims. */
    private fun fileOf(url: String): File? {
        if (!isWaiting(url)) return null
        val file = File(url.toUri().path ?: return null)
        return file.takeIf { it.parentFile?.canonicalPath == folder.canonicalPath }
    }

    companion object {
        /** True for a photo still on this phone only. */
        fun isWaiting(url: String): Boolean = url.startsWith(FILE_SCHEME)
    }
}
