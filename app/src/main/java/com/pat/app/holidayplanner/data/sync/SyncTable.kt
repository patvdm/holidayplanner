package com.pat.app.holidayplanner.data.sync

import com.pat.app.holidayplanner.data.local.SyncDao
import com.pat.app.holidayplanner.data.local.SyncEntity

/** A row that has been handed to the server, remembered so its dirty flag can be cleared safely. */
data class PushedRow(val id: String, val updatedAt: Long, val values: Map<String, String>)

/**
 * Binds one sheet tab to one Room table. Every method hides the entity type, so the sync code can
 * hold a list of tables without caring which is which.
 */
class SyncTable<E : SyncEntity>(
    val sheetName: String,
    private val dao: SyncDao<E>,
    private val toRow: (E) -> Map<String, String>,
    private val fromRow: (SheetRow) -> E?,
) {

    suspend fun pendingRows(limit: Int): List<PushedRow> =
        dao.dirty(limit).map { PushedRow(it.meta.id, it.meta.updatedAt, toRow(it)) }

    /** Only rows whose `updatedAt` is unchanged lose their dirty flag. */
    suspend fun clearDirty(rows: List<PushedRow>) {
        rows.forEach { dao.clearDirty(it.id, it.updatedAt) }
    }

    /**
     * Applies the merge rule to every incoming row and returns how many were taken.
     *
     * A local row that is dirty and newer than the remote one wins and keeps its dirty flag, so it
     * goes out on the next push. Everything else is replaced by the server's version.
     */
    suspend fun merge(rows: List<Map<String, String>>): Int {
        val accepted = rows.mapNotNull { values ->
            val remote = fromRow(SheetRow(values)) ?: return@mapNotNull null
            val local = dao.find(remote.meta.id)
            remote.takeUnless { localWins(local, remote) }
        }
        if (accepted.isNotEmpty()) dao.upsert(accepted)
        return accepted.size
    }

    private fun localWins(local: E?, remote: E): Boolean =
        local != null && local.meta.dirty && local.meta.updatedAt > remote.meta.updatedAt
}
