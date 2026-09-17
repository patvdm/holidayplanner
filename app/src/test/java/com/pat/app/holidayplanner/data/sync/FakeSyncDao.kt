package com.pat.app.holidayplanner.data.sync

import com.pat.app.holidayplanner.data.local.SyncDao
import com.pat.app.holidayplanner.data.local.SyncEntity
import com.pat.app.holidayplanner.data.local.SyncMeta
import com.pat.app.holidayplanner.data.local.UserEntity

/** In-memory stand-in for a Room DAO, with the same dirty-flag semantics as the real queries. */
class FakeSyncDao<E : SyncEntity>(
    private val withMeta: (E, SyncMeta) -> E,
) : SyncDao<E> {

    val rows = linkedMapOf<String, E>()

    override suspend fun dirty(limit: Int): List<E> =
        rows.values.filter { it.meta.dirty }.take(limit)

    override suspend fun find(id: String): E? = rows[id]

    override suspend fun upsert(entities: List<E>) {
        entities.forEach { rows[it.meta.id] = it }
    }

    /** Mirrors "UPDATE ... SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt". */
    override suspend fun clearDirty(id: String, updatedAt: Long) {
        val current = rows[id] ?: return
        if (current.meta.updatedAt != updatedAt) return
        rows[id] = withMeta(current, current.meta.copy(dirty = false))
    }
}

fun fakeUserDao(): FakeSyncDao<UserEntity> = FakeSyncDao { entity, meta -> entity.copy(meta = meta) }

fun user(
    id: String,
    name: String,
    updatedAt: Long,
    dirty: Boolean,
    syncedAt: Long = 0,
    deleted: Boolean = false,
): UserEntity = UserEntity(
    meta = SyncMeta(
        id = id,
        createdAt = 1,
        createdBy = id,
        updatedAt = updatedAt,
        updatedBy = id,
        syncedAt = syncedAt,
        deleted = deleted,
        dirty = dirty,
    ),
    name = name,
)
