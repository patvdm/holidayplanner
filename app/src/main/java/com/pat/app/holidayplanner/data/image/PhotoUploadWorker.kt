package com.pat.app.holidayplanner.data.image

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pat.app.holidayplanner.data.repo.AttachmentRepository
import com.pat.app.holidayplanner.data.repo.UploadOutcome
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private const val TAG = "PhotoUploadWorker"

/**
 * Sends one waiting photo. Retries with growing gaps for as long as WorkManager allows; if it still
 * gives up, the photo stays waiting and the next app start queues it again.
 */
class PhotoUploadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    private val attachments: AttachmentRepository by inject()

    override suspend fun doWork(): Result {
        val linkId = inputData.getString(LINK_ID).orEmpty()
        if (linkId.isEmpty()) return Result.failure()
        return when (attachments.finishUpload(linkId)) {
            UploadOutcome.DONE -> Result.success()
            UploadOutcome.RETRY -> {
                Log.w(TAG, "upload of $linkId failed on attempt $runAttemptCount, trying again later")
                Result.retry()
            }
        }
    }

    companion object {
        const val LINK_ID = "linkId"
    }
}
