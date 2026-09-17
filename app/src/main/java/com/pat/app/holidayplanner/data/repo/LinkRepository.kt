package com.pat.app.holidayplanner.data.repo

import com.pat.app.holidayplanner.data.local.LinkEntity
import com.pat.app.holidayplanner.data.local.SyncMeta
import com.pat.app.holidayplanner.data.local.dao.LinkDao
import com.pat.app.holidayplanner.domain.LinkSource
import com.pat.app.holidayplanner.domain.newId
import kotlinx.coroutines.flow.Flow

class LinkRepository(
    private val dao: LinkDao,
    private val writer: LocalWriter,
) {

    /** Links that have not been attached to a spot yet. */
    fun observeLinks(spotId: String): Flow<List<LinkEntity>> = dao.observeBySpot(spotId)

    suspend fun findByUrl(url: String): LinkEntity? = dao.findByUrl(url)

    /** Every link belongs to a spot: one with nowhere to live is not worth keeping. */
    suspend fun add(url: String, source: LinkSource, spotId: String, note: String = ""): String {
        val stamp = writer.stamp()
        val id = newId()
        val link = LinkEntity(
            meta = SyncMeta.created(id, stamp.now, stamp.userId),
            spotId = spotId,
            url = url.trim(),
            source = source,
            note = note.trim(),
        )
        dao.upsert(listOf(link))
        writer.requestSync()
        return id
    }

    suspend fun setNote(linkId: String, note: String) = edit(linkId) { it.copy(note = note.trim()) }

    suspend fun delete(linkId: String) {
        val current = dao.find(linkId) ?: return
        val stamp = writer.stamp()
        dao.upsert(listOf(current.copy(meta = current.meta.touched(stamp.now, stamp.userId, deleted = true))))
        writer.requestSync()
    }

    private suspend fun edit(id: String, change: (LinkEntity) -> LinkEntity) {
        val current = dao.find(id) ?: return
        val stamp = writer.stamp()
        val updated = change(current)
        dao.upsert(listOf(updated.copy(meta = updated.meta.touched(stamp.now, stamp.userId))))
        writer.requestSync()
    }
}
