package com.pat.app.holidayplanner.data.repo

import com.pat.app.holidayplanner.data.local.RouteEntity
import com.pat.app.holidayplanner.data.local.RouteLinePoint
import com.pat.app.holidayplanner.data.local.RouteRow
import com.pat.app.holidayplanner.data.local.RouteStopEntity
import com.pat.app.holidayplanner.data.local.RouteStopRow
import com.pat.app.holidayplanner.data.local.SyncMeta
import com.pat.app.holidayplanner.data.local.dao.CascadeDao
import com.pat.app.holidayplanner.data.local.dao.RouteDao
import com.pat.app.holidayplanner.data.local.dao.RouteStopDao
import com.pat.app.holidayplanner.data.sync.Transactor
import com.pat.app.holidayplanner.domain.SortKey
import com.pat.app.holidayplanner.domain.newId
import kotlinx.coroutines.flow.Flow

/** Routes and the stops on them: a walk through one city's spots, in order. */
class RouteRepository(
    private val dao: RouteDao,
    private val stopDao: RouteStopDao,
    private val cascadeDao: CascadeDao,
    private val transactor: Transactor,
    private val writer: LocalWriter,
) {

    fun observeRoutes(): Flow<List<RouteRow>> = dao.observeRows()

    fun observeRoutesInCountry(countryId: String): Flow<List<RouteRow>> = dao.observeRowsInCountry(countryId)

    fun observeRoute(id: String): Flow<RouteEntity?> = dao.observeById(id)

    fun observeStops(routeId: String): Flow<List<RouteStopRow>> = stopDao.observeRows(routeId)

    suspend fun findRoute(id: String): RouteEntity? = dao.find(id)

    /** The positioned stops of every route still to walk, in walking order, for drawing on the map. */
    fun observeLinePoints(): Flow<List<RouteLinePoint>> = stopDao.observeLinePoints()

    /** The ids of the routes [spotId] is a stop on. */
    fun observeRouteIdsWithSpot(spotId: String): Flow<List<String>> = stopDao.observeRouteIdsWithSpot(spotId)

    /** Takes a spot off one route, if it is on it. The spot itself is untouched. */
    suspend fun removeSpot(routeId: String, spotId: String) {
        stopDao.findBySpot(routeId, spotId)?.let { removeStop(it) }
    }

    /**
     * A new route through [cityId], with [spotIds] already on it in the order given. Every entry
     * point creates a route this way, whether the spots were preselected or the list is empty.
     */
    suspend fun create(cityId: String, name: String, spotIds: List<String> = emptyList()): String {
        val stamp = writer.stamp()
        val id = newId()
        dao.upsert(
            listOf(
                RouteEntity(
                    meta = SyncMeta.created(id, stamp.now, stamp.userId),
                    cityId = cityId,
                    name = name.trim(),
                    completed = false,
                    notes = "",
                ),
            ),
        )
        spotIds.forEach { addStop(id, it) }
        writer.requestSync()
        return id
    }

    suspend fun rename(id: String, name: String) = editRoute(id) { it.copy(name = name.trim()) }

    suspend fun setCompleted(id: String, completed: Boolean) = editRoute(id) { it.copy(completed = completed) }

    suspend fun setNotes(id: String, notes: String) = editRoute(id) { it.copy(notes = notes) }

    /** Removes the route and its stops. The spots themselves are untouched. */
    suspend fun delete(id: String) {
        val stamp = writer.stamp()
        transactor.transaction {
            cascadeDao.deleteStopsOfRoute(id, stamp.now, stamp.userId)
            cascadeDao.deleteRoute(id, stamp.now, stamp.userId)
        }
        writer.requestSync()
    }

    /**
     * Puts a spot on the end of a route, or does nothing if it is already on it. A walk where the
     * same place appears twice is nearly always a double tap rather than a plan to go back.
     */
    suspend fun addStop(routeId: String, spotId: String): String? {
        if (stopDao.findBySpot(routeId, spotId) != null) return null
        val stamp = writer.stamp()
        val id = newId()
        stopDao.upsert(
            listOf(
                RouteStopEntity(
                    meta = SyncMeta.created(id, stamp.now, stamp.userId),
                    routeId = routeId,
                    spotId = spotId,
                    sortKey = SortKey.after(stopDao.lastSortKey(routeId)),
                    // Somewhere just added is somewhere you mean to go, so it is in the directions.
                    onMaps = true,
                ),
            ),
        )
        writer.requestSync()
        return id
    }

    suspend fun removeStop(id: String) {
        val current = stopDao.find(id) ?: return
        val stamp = writer.stamp()
        stopDao.upsert(listOf(current.copy(meta = current.meta.touched(stamp.now, stamp.userId, deleted = true))))
        writer.requestSync()
    }

    /** Puts a stop into the Google Maps route, or leaves it out while keeping it on the walk. */
    suspend fun setOnMaps(id: String, onMaps: Boolean) = editStop(id) { it.copy(onMaps = onMaps) }

    suspend fun move(id: String, previousKey: String?, nextKey: String?) =
        editStop(id) { it.copy(sortKey = SortKey.between(previousKey, nextKey)) }

    /**
     * Rearranges some of a route's stops among the places they already occupy, which is what
     * sorting by proximity does. Only the stops given move, and they land on their own keys in the
     * new order, so the stops left out of the sort keep exactly where they were in the list.
     */
    suspend fun reorder(stopIdsInOrder: List<String>) {
        val stops = stopIdsInOrder.mapNotNull { stopDao.find(it) }
        if (stops.isEmpty()) return
        val keys = stops.map { it.sortKey }.sorted()
        val stamp = writer.stamp()
        val updated = stops.mapIndexed { index, stop ->
            stop.copy(sortKey = keys[index], meta = stop.meta.touched(stamp.now, stamp.userId))
        }
        stopDao.upsert(updated)
        writer.requestSync()
    }

    private suspend fun editRoute(id: String, change: (RouteEntity) -> RouteEntity) {
        val current = dao.find(id) ?: return
        val stamp = writer.stamp()
        val changed = change(current)
        dao.upsert(listOf(changed.copy(meta = changed.meta.touched(stamp.now, stamp.userId))))
        writer.requestSync()
    }

    private suspend fun editStop(id: String, change: (RouteStopEntity) -> RouteStopEntity) {
        val current = stopDao.find(id) ?: return
        val stamp = writer.stamp()
        val changed = change(current)
        stopDao.upsert(listOf(changed.copy(meta = changed.meta.touched(stamp.now, stamp.userId))))
        writer.requestSync()
    }
}
