package com.pat.app.holidayplanner.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

private const val WORK_NOW = "sync_now"
private const val WORK_DEBOUNCED = "sync_debounced"
private const val WORK_PERIODIC = "sync_periodic"
private const val WORK_DEADLINE = "sync_deadline"

private val DEBOUNCE = 3.seconds

/** The longest a write waits for its sync, however many more writes keep arriving. */
private val MAX_WAIT = 30.seconds
private val PERIOD = 15.minutes

/**
 * Decides when [SyncWorker] runs. Every request needs a network connection and backs off
 * exponentially, so a failed sync retries without the app doing anything.
 */
class SyncScheduler(context: Context) {

    private val workManager = WorkManager.getInstance(context.applicationContext)

    /** Called once per process: sync straight away and make sure the periodic run exists. */
    fun onAppStart() {
        syncNow()
        workManager.enqueueUniquePeriodicWork(
            WORK_PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SyncWorker>(PERIOD.toJavaDuration()).applyDefaults().build(),
        )
    }

    /** Pull to refresh, "Sync now", and the initial pull during onboarding. */
    fun syncNow() {
        enqueue(WORK_NOW, Duration.ZERO)
    }

    /**
     * Called after a local write. Replacing the pending request debounces a burst of edits into one
     * sync a few seconds after the last of them. A steady stream of writes, such as a background
     * fill saving a spot every second or two, would push that back for ever, so the first write
     * also sets a deadline that later ones leave alone.
     */
    fun syncAfterLocalWrite() {
        enqueue(WORK_DEBOUNCED, DEBOUNCE)
        enqueue(WORK_DEADLINE, MAX_WAIT, ExistingWorkPolicy.KEEP)
    }

    private fun enqueue(name: String, delay: Duration, policy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setInitialDelay(delay.toJavaDuration())
            .applyDefaults()
            .build()
        workManager.enqueueUniqueWork(name, policy, request)
    }
}

private fun <B : WorkRequest.Builder<B, *>> B.applyDefaults(): B = this
    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
