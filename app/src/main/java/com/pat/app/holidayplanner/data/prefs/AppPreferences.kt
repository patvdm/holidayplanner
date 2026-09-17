package com.pat.app.holidayplanner.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pat.app.holidayplanner.data.sync.SyncOutcomeStore
import com.pat.app.holidayplanner.domain.SortMode
import com.pat.app.holidayplanner.domain.SortScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "holiday_planner_prefs")

/** Backend URL and secret entered in Settings → Advanced. Blank means "use BuildConfig". */
data class BackendOverride(val url: String, val secret: String)

/** How the last sync run ended. Losing this is harmless, so it lives outside the database. */
data class SyncOutcome(val lastSuccessAt: Long?, val lastError: String?)

/**
 * Local-only preferences. Nothing here is synced, and nothing here is needed to rebuild the data:
 * `lastServerTime` deliberately lives in the database instead, next to the rows it describes.
 */
class AppPreferences(context: Context) : SyncOutcomeStore {
    private val store = context.dataStore

    /** Null until onboarding picks a user. Every local write is attributed to this id. */
    val currentUserId: Flow<String?> = store.data.map { prefs ->
        prefs[KEY_CURRENT_USER_ID]?.takeIf(String::isNotBlank)
    }

    val backendOverride: Flow<BackendOverride> = store.data.map { prefs ->
        BackendOverride(
            url = prefs[KEY_BACKEND_URL].orEmpty(),
            secret = prefs[KEY_BACKEND_SECRET].orEmpty(),
        )
    }

    val syncOutcome: Flow<SyncOutcome> = store.data.map { prefs ->
        SyncOutcome(
            lastSuccessAt = prefs[KEY_LAST_SYNC_AT],
            lastError = prefs[KEY_LAST_SYNC_ERROR]?.takeIf(String::isNotBlank),
        )
    }

    /** The sort mode chosen for one list. Unknown or unset values fall back to manual order. */
    fun sortMode(scope: SortScope): Flow<SortMode> = store.data.map { prefs ->
        scope.parse(prefs[sortModeKey(scope)])
    }

    suspend fun setSortMode(scope: SortScope, mode: SortMode) {
        store.edit { it[sortModeKey(scope)] = mode.name }
    }

    /**
     * The inbox link whose app was just opened. Stored here rather than in memory because the
     * system can kill this process while Instagram is in the foreground, and the assign sheet still
     * has to appear when the person comes back.
     */
    val pendingAssignLinkId: Flow<String?> = store.data.map { prefs ->
        prefs[KEY_PENDING_ASSIGN]?.takeIf(String::isNotBlank)
    }

    suspend fun setPendingAssignLinkId(linkId: String) {
        store.edit { it[KEY_PENDING_ASSIGN] = linkId }
    }

    suspend fun clearPendingAssignLinkId() {
        store.edit { it.remove(KEY_PENDING_ASSIGN) }
    }

    suspend fun setCurrentUserId(userId: String) {
        store.edit { it[KEY_CURRENT_USER_ID] = userId }
    }

    suspend fun clearCurrentUserId() {
        store.edit { it.remove(KEY_CURRENT_USER_ID) }
    }

    suspend fun setBackendOverride(url: String, secret: String) {
        store.edit { prefs ->
            prefs[KEY_BACKEND_URL] = url.trim()
            prefs[KEY_BACKEND_SECRET] = secret.trim()
        }
    }

    override suspend fun recordSyncSuccess(at: Long) {
        store.edit { prefs ->
            prefs[KEY_LAST_SYNC_AT] = at
            prefs.remove(KEY_LAST_SYNC_ERROR)
        }
    }

    override suspend fun recordSyncError(message: String) {
        store.edit { it[KEY_LAST_SYNC_ERROR] = message }
    }

    private companion object {
        fun sortModeKey(scope: SortScope) = stringPreferencesKey("sort_mode_${scope.prefKey}")

        val KEY_CURRENT_USER_ID = stringPreferencesKey("current_user_id")
        val KEY_PENDING_ASSIGN = stringPreferencesKey("pending_assign_link_id")
        val KEY_BACKEND_URL = stringPreferencesKey("backend_url")
        val KEY_BACKEND_SECRET = stringPreferencesKey("backend_secret")
        val KEY_LAST_SYNC_AT = longPreferencesKey("last_sync_at")
        val KEY_LAST_SYNC_ERROR = stringPreferencesKey("last_sync_error")
    }
}
