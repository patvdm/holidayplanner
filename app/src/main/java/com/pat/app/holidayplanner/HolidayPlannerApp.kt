package com.pat.app.holidayplanner

import android.app.Application
import com.pat.app.holidayplanner.data.repo.CoordinateBackfill
import com.pat.app.holidayplanner.data.sync.SyncScheduler
import com.pat.app.holidayplanner.di.appModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class HolidayPlannerApp : Application() {

    private val syncScheduler: SyncScheduler by inject()
    private val coordinateBackfill: CoordinateBackfill by inject()

    /** Outlives every screen, for the one-off repair below. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@HolidayPlannerApp)
            modules(appModule)
        }
        syncScheduler.onAppStart()
        // Spots saved before their link could be followed have no position; repair them once.
        appScope.launch { coordinateBackfill.run() }
    }
}
