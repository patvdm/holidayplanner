package com.pat.app.holidayplanner.data.sync

import com.pat.app.holidayplanner.data.local.dao.SyncStateDao
import com.pat.app.holidayplanner.data.prefs.AppPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Where a finished sync run reports itself. Implemented by the preferences, faked in tests. */
interface SyncOutcomeStore {
    suspend fun recordSyncSuccess(at: Long)

    suspend fun recordSyncError(message: String)
}

data class SyncStatus(
    val running: Boolean = false,
    val pendingCount: Int = 0,
    val lastSuccessAt: Long? = null,
    val lastError: String? = null,
)

/** What Settings and the sync indicator show: is it running, how much is waiting, did it fail. */
class SyncStatusRepository(
    repository: SyncRepository,
    prefs: AppPreferences,
    syncStateDao: SyncStateDao,
) {
    val status: Flow<SyncStatus> = combine(
        repository.running,
        syncStateDao.observePendingCount(),
        prefs.syncOutcome,
    ) { running, pending, outcome ->
        SyncStatus(
            running = running,
            pendingCount = pending,
            lastSuccessAt = outcome.lastSuccessAt,
            lastError = outcome.lastError,
        )
    }
}
