package com.pat.app.holidayplanner.data.repo

import com.pat.app.holidayplanner.data.prefs.AppPreferences
import com.pat.app.holidayplanner.data.sync.SyncScheduler
import com.pat.app.holidayplanner.domain.AppClock
import kotlinx.coroutines.flow.first

/** The time and author of one local write. */
data class Stamp(val now: Long, val userId: String)

/**
 * Every local write is stamped with the current time and user and marked dirty, then asks for a
 * debounced sync. Repositories share this instead of each holding a clock, the preferences and the
 * scheduler.
 */
class LocalWriter(
    private val prefs: AppPreferences,
    private val clock: AppClock,
    private val scheduler: SyncScheduler,
) {

    /**
     * [userId] is only passed when the row is the current user's own first row, which is written
     * before the user is stored in preferences.
     */
    suspend fun stamp(userId: String? = null): Stamp =
        Stamp(clock.now(), userId ?: prefs.currentUserId.first().orEmpty())

    fun requestSync() = scheduler.syncAfterLocalWrite()
}
