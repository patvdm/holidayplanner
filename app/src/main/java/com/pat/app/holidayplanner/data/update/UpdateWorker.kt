package com.pat.app.holidayplanner.data.update

import com.pat.app.holidayplanner.data.sync.SyncRepository
import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

private const val WORK_START = "update_check_start"
private const val WORK_PERIODIC = "update_check_periodic"
private const val PERIOD_HOURS = 1L

/**
 * Looks for a new version in the background, only on Wi-Fi because an APK is large. When one is
 * downloaded and nobody is using the app, it tries to install straight away; Android lets that
 * through without a tap once the app has updated itself once.
 */
class UpdateWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    private val updater: AppUpdater by inject()
    private val sync: SyncRepository by inject()

    override suspend fun doWork(): Result {
        val found = updater.check(allowDownload = true)
        // Edits not yet on the sheet would be lost if the update rebuilds the database, so it waits
        // for the next hourly check rather than installing over them.
        if (found == UpdateCheck.READY && !AppVisibility.visible && sync.sendPending() == 0) {
            updater.install(interactive = false)
        }
        return if (found == UpdateCheck.FAILED) Result.retry() else Result.success()
    }
}

/** Schedules [UpdateWorker]: once at every app start, and every hour after. */
class UpdateScheduler(context: Context) {

    private val workManager = WorkManager.getInstance(context.applicationContext)
    private val onWifi = Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build()

    fun onAppStart() {
        workManager.enqueueUniqueWork(
            WORK_START,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<UpdateWorker>().setConstraints(onWifi).build(),
        )
        workManager.enqueueUniquePeriodicWork(
            WORK_PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<UpdateWorker>(PERIOD_HOURS, TimeUnit.HOURS).setConstraints(onWifi).build(),
        )
    }
}

/**
 * Whether any screen of the app is showing. An update installed while it is closes the app under
 * the person's hands, so the background install waits until nothing is on screen.
 */
object AppVisibility : Application.ActivityLifecycleCallbacks {

    private var started = 0

    val visible: Boolean get() = started > 0

    override fun onActivityStarted(activity: Activity) {
        started++
    }

    override fun onActivityStopped(activity: Activity) {
        started = (started - 1).coerceAtLeast(0)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

    override fun onActivityResumed(activity: Activity) = Unit

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) = Unit
}
