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

    /** What hangs on one spot. */
    fun observeLinks(spotId: String): Flow<List<LinkEntity>> = dao.observeBySpot(spotId)

    /** Everything hanging on a country, its cities and their spots, ready to be sorted by owner. */
    fun observeInCountry(countryId: String): Flow<List<LinkEntity>> = dao.observeInCountry(countryId)

    suspend fun findByUrl(url: String): LinkEntity? = dao.findByUrl(url)

    /**
     * Every link belongs to a spot, a city or a country: one with nowhere to live is not worth
     * keeping, and would show up on nothing.
     */
    suspend fun add(url: String, source: LinkSource, owner: AttachmentOwner): String {
        val stamp = writer.stamp()
        val id = newId()
        val link = LinkEntity(
            meta = SyncMeta.created(id, stamp.now, stamp.userId),
            spotId = owner.spotId,
            cityId = owner.cityId,
            countryId = owner.countryId,
            url = url.trim(),
            source = source,
            note = "",
        )
        dao.upsert(listOf(link))
        writer.requestSync()
        return id
    }

    /** Hangs a link on something else. The old owner is cleared, so it never shows in two places. */
    suspend fun reassign(linkId: String, owner: AttachmentOwner) {
        val current = dao.find(linkId) ?: return
        val stamp = writer.stamp()
        val moved = current.copy(
            spotId = owner.spotId,
            cityId = owner.cityId,
            countryId = owner.countryId,
            meta = current.meta.touched(stamp.now, stamp.userId),
        )
        dao.upsert(listOf(moved))
        writer.requestSync()
    }

    suspend fun delete(linkId: String) {
        val current = dao.find(linkId) ?: return
        val stamp = writer.stamp()
        dao.upsert(listOf(current.copy(meta = current.meta.touched(stamp.now, stamp.userId, deleted = true))))
        writer.requestSync()
    }
}
