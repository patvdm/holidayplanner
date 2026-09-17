package com.pat.app.holidayplanner

import android.app.Application
import com.pat.app.holidayplanner.data.sync.SyncScheduler
import com.pat.app.holidayplanner.di.appModule
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class HolidayPlannerApp : Application() {

    private val syncScheduler: SyncScheduler by inject()

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@HolidayPlannerApp)
            modules(appModule)
        }
        syncScheduler.onAppStart()
    }
}
