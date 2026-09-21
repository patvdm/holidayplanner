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

    /** What hangs on the city itself. */
    fun observeForCity(cityId: String): Flow<List<LinkEntity>> = dao.observeByCity(cityId)

    /** Everything hanging on the spots of one city, ready to be grouped by spot. */
    fun observeInCity(cityId: String): Flow<List<LinkEntity>> = dao.observeBySpotsIn(cityId)

    suspend fun findByUrl(url: String): LinkEntity? = dao.findByUrl(url)

    /**
     * Every link belongs to a spot or to a city: one with nowhere to live is not worth keeping,
     * and would show up on nothing.
     */
    suspend fun add(url: String, source: LinkSource, spotId: String = "", cityId: String = ""): String {
        val stamp = writer.stamp()
        val id = newId()
        val link = LinkEntity(
            meta = SyncMeta.created(id, stamp.now, stamp.userId),
            spotId = spotId,
            cityId = cityId,
            url = url.trim(),
            source = source,
            note = "",
        )
        dao.upsert(listOf(link))
        writer.requestSync()
        return id
    }

    suspend fun delete(linkId: String) {
        val current = dao.find(linkId) ?: return
        val stamp = writer.stamp()
        dao.upsert(listOf(current.copy(meta = current.meta.touched(stamp.now, stamp.userId, deleted = true))))
        writer.requestSync()
    }
}
