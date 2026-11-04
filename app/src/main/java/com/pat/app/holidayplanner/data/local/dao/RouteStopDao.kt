package com.pat.app.holidayplanner.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.pat.app.holidayplanner.data.local.RouteLinePoint
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

    /**
     * Every stop with a position on every route, route by route in walking order, which is what
     * the map draws its lines through. Walked routes come too, marked, for showing one on request.
     */
    @Query(
        """
        SELECT r.id AS routeId, r.name AS routeName, r.cityId AS cityId, r.createdAt AS createdAt,
            r.completed AS completed, s.lat AS lat, s.lng AS lng
        FROM route_stops st
        JOIN routes r ON r.id = st.routeId
        JOIN spots s ON s.id = st.spotId
        WHERE st.deleted = 0 AND r.deleted = 0 AND s.deleted = 0
            AND s.lat IS NOT NULL AND s.lng IS NOT NULL
        ORDER BY r.id, st.sortKey, st.id
        """,
    )
    fun observeLinePoints(): Flow<List<RouteLinePoint>>

    /** The routes a spot is a stop on, so a picker can tick them. */
    @Query(
        """
        SELECT DISTINCT st.routeId FROM route_stops st
        JOIN routes r ON r.id = st.routeId
        WHERE st.spotId = :spotId AND st.deleted = 0 AND r.deleted = 0
        """,
    )
    fun observeRouteIdsWithSpot(spotId: String): Flow<List<String>>

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
