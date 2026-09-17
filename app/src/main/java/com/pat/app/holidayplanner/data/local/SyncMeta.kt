package com.pat.app.holidayplanner.data.local

/**
 * The columns every table shares. Embedded into each entity, so they are plain columns in SQLite.
 *
 * `syncedAt` is written by the server only and drives the pull filter. `dirty` is local: it marks a
 * row that still has to be pushed and never leaves the device.
 */
data class SyncMeta(
    val id: String,
    val createdAt: Long,
    val createdBy: String,
    val updatedAt: Long,
    val updatedBy: String,
    val syncedAt: Long,
    val deleted: Boolean,
    val dirty: Boolean,
) {
    /** Marks the row as locally changed at [at] by [by]. */
    fun touched(at: Long, by: String, deleted: Boolean = this.deleted): SyncMeta =
        copy(updatedAt = at, updatedBy = by, deleted = deleted, dirty = true)

    companion object {
        fun created(id: String, at: Long, by: String): SyncMeta = SyncMeta(
            id = id,
            createdAt = at,
            createdBy = by,
            updatedAt = at,
            updatedBy = by,
            syncedAt = 0,
            deleted = false,
            dirty = true,
        )
    }
}

/** Every synced entity exposes its common columns the same way. */
interface SyncEntity {
    val meta: SyncMeta
}
