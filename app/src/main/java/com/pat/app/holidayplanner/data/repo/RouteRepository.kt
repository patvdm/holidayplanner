package com.pat.app.holidayplanner.data.repo

import com.pat.app.holidayplanner.data.local.RouteStopEntity
import com.pat.app.holidayplanner.data.local.RouteStopRow
import com.pat.app.holidayplanner.data.local.SyncMeta
import com.pat.app.holidayplanner.data.local.dao.RouteStopDao
import com.pat.app.holidayplanner.domain.SortKey
import com.pat.app.holidayplanner.domain.newId
import kotlinx.coroutines.flow.Flow

/** The stops of a day route: what is on it, in what order. */
class RouteRepository(
    private val dao: RouteStopDao,
    private val writer: LocalWriter,
) {

    fun observeStops(tripItemId: String): Flow<List<RouteStopRow>> = dao.observeRows(tripItemId)

    /**
     * Puts a spot on the end of a route, or does nothing if it is already on it. A day where the
     * same place appears twice is nearly always a double tap rather than a plan to go back.
     */
    suspend fun addStop(tripItemId: String, spotId: String): String? {
        if (dao.findBySpot(tripItemId, spotId) != null) return null
        val stamp = writer.stamp()
        val id = newId()
        dao.upsert(
            listOf(
                RouteStopEntity(
                    meta = SyncMeta.created(id, stamp.now, stamp.userId),
                    tripItemId = tripItemId,
                    spotId = spotId,
                    sortKey = SortKey.after(dao.lastSortKey(tripItemId)),
                ),
            ),
        )
        writer.requestSync()
        return id
    }

    /** Adds several spots at once, keeping the order they were given in. */
    suspend fun addStops(tripItemId: String, spotIds: List<String>) {
        spotIds.forEach { addStop(tripItemId, it) }
    }

    suspend fun removeStop(id: String) {
        val current = dao.find(id) ?: return
        val stamp = writer.stamp()
        dao.upsert(listOf(current.copy(meta = current.meta.touched(stamp.now, stamp.userId, deleted = true))))
        writer.requestSync()
    }

    suspend fun move(id: String, previousKey: String?, nextKey: String?) =
        edit(id) { it.copy(sortKey = SortKey.between(previousKey, nextKey)) }

    /**
     * Rewrites the order of a whole route, which is what sorting by proximity does. Every stop gets
     * a fresh key so the list reads in the given order, one write each.
     */
    suspend fun reorder(stopIdsInOrder: List<String>) {
        val stamp = writer.stamp()
        var previous: String? = null
        val updated = stopIdsInOrder.mapNotNull { id ->
            val stop = dao.find(id) ?: return@mapNotNull null
            val key = SortKey.after(previous)
            previous = key
            stop.copy(sortKey = key, meta = stop.meta.touched(stamp.now, stamp.userId))
        }
        if (updated.isEmpty()) return
        dao.upsert(updated)
        writer.requestSync()
    }

    private suspend fun edit(id: String, change: (RouteStopEntity) -> RouteStopEntity) {
        val current = dao.find(id) ?: return
        val stamp = writer.stamp()
        val changed = change(current)
        dao.upsert(listOf(changed.copy(meta = changed.meta.touched(stamp.now, stamp.userId))))
        writer.requestSync()
    }
}
