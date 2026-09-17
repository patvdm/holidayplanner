package com.pat.app.holidayplanner.data.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private const val TAG = "SyncWorker"
private const val MAX_ATTEMPTS = 5

/**
 * Runs one sync in the background. Dependencies come from Koin directly, which avoids replacing
 * WorkManager's default initialisation just to inject two objects.
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    private val repository: SyncRepository by inject()

    override suspend fun doWork(): Result = repository.sync().fold(
        onSuccess = { Result.success() },
        onFailure = { error ->
            Log.w(TAG, "sync failed on attempt $runAttemptCount: ${error.message}")
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        },
    )
}
