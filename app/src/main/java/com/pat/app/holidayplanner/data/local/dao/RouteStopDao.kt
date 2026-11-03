package com.pat.app.holidayplanner.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.pat.app.holidayplanner.data.local.RouteStopEntity
import com.pat.app.holidayplanner.data.local.RouteStopRow
import com.pat.app.holidayplanner.data.local.SyncDao
import kotlinx.coroutines.flow.Flow

@Dao
interface RouteStopDao : SyncDao<RouteStopEntity> {

    @Query("SELECT * FROM route_stops WHERE routeId = :routeId AND deleted = 0 ORDER BY sortKey, id")
    fun observeByRoute(routeId: String): Flow<List<RouteStopEntity>>

    /** The stops of one route in order, each carrying the spot it stands for. */
    @Query(
        """
        SELECT r.id AS stopId, r.sortKey AS sortKey, r.onMaps AS onMaps, r.spotId AS spotId,
            s.name AS name, s.parentCityId AS cityId, COALESCE(c.name, '') AS cityName, s.address AS address,
            s.lat AS lat, s.lng AS lng, s.googlePlaceId AS googlePlaceId,
            s.imageUrl AS imageUrl, s.notes AS notes, s.marks AS marks,
            s.priority AS priority, s.status AS status, s.type AS type
        FROM route_stops r
        JOIN spots s ON s.id = r.spotId
        LEFT JOIN cities c ON c.id = s.parentCityId
        WHERE r.routeId = :routeId AND r.deleted = 0 AND s.deleted = 0
        ORDER BY r.sortKey, r.id
        """,
    )
    fun observeRows(routeId: String): Flow<List<RouteStopRow>>

    @Query(
        """
        SELECT id FROM route_stops
        WHERE routeId = :routeId AND spotId = :spotId AND deleted = 0 LIMIT 1
        """,
    )
    suspend fun findBySpot(routeId: String, spotId: String): String?

    @Query(
        """
        SELECT sortKey FROM route_stops
        WHERE routeId = :routeId AND deleted = 0
        ORDER BY sortKey DESC, id DESC LIMIT 1
        """,
    )
    suspend fun lastSortKey(routeId: String): String?

    @Query("SELECT * FROM route_stops WHERE dirty = 1 ORDER BY updatedAt LIMIT :limit")
    override suspend fun dirty(limit: Int): List<RouteStopEntity>

    @Query("SELECT * FROM route_stops WHERE id = :id")
    override suspend fun find(id: String): RouteStopEntity?

    @Upsert
    override suspend fun upsert(entities: List<RouteStopEntity>)

    @Query("UPDATE route_stops SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt")
    override suspend fun clearDirty(id: String, updatedAt: Long)
}
