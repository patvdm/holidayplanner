package com.pat.app.holidayplanner.data.repo

import com.pat.app.holidayplanner.data.local.SyncMeta
import com.pat.app.holidayplanner.data.local.UserEntity
import com.pat.app.holidayplanner.data.local.dao.UserDao
import com.pat.app.holidayplanner.data.prefs.AppPreferences
import com.pat.app.holidayplanner.domain.newId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

class UserRepository(
    private val dao: UserDao,
    private val prefs: AppPreferences,
    private val writer: LocalWriter,
) {

    fun observeUsers(): Flow<List<UserEntity>> = dao.observeAll()

    /** How many people share this planner, used to spot a place everyone hearted. */
    fun observeUserCount(): Flow<Int> = dao.observeCount()

    val currentUserId: Flow<String?> = prefs.currentUserId

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeCurrentUser(): Flow<UserEntity?> = prefs.currentUserId.flatMapLatest { id ->
        if (id == null) flowOf(null) else dao.observeById(id)
    }

    /**
     * Creates the row for a new person and signs them in. The row is attributed to itself, because
     * there is no current user yet at that moment.
     */
    suspend fun createAndSelect(name: String): String {
        val id = newId()
        val stamp = writer.stamp(userId = id)
        dao.upsert(listOf(UserEntity(meta = SyncMeta.created(id, stamp.now, stamp.userId), name = name.trim())))
        prefs.setCurrentUserId(id)
        writer.requestSync()
        return id
    }

    suspend fun select(userId: String) = prefs.setCurrentUserId(userId)

    /** Returns to onboarding. The user row itself is left alone. */
    suspend fun signOut() = prefs.clearCurrentUserId()
}
