package com.pat.app.holidayplanner.data.local

/**
 * The four operations sync needs from every table. Each Room DAO re-declares them with its own
 * annotated queries, which lets the sync code treat all tables the same way.
 */
interface SyncDao<E> {
    suspend fun dirty(limit: Int): List<E>

    suspend fun find(id: String): E?

    suspend fun upsert(entities: List<E>)

    /**
     * Clears the dirty flag only if the row still carries the [updatedAt] that was pushed, so an
     * edit made while the push was in flight stays dirty and goes out on the next run.
     */
    suspend fun clearDirty(id: String, updatedAt: Long)
}
