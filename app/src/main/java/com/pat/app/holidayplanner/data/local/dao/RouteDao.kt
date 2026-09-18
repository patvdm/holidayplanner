package com.pat.app.holidayplanner.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.pat.app.holidayplanner.data.local.RouteEntity
import com.pat.app.holidayplanner.data.local.RouteRow
import com.pat.app.holidayplanner.data.local.SyncDao
import kotlinx.coroutines.flow.Flow

@Dao
interface RouteDao : SyncDao<RouteEntity> {

    /** Every route with the city it walks and how many stops it has, newest first. */
    @Query(
        """
        SELECT r.*, c.name AS cityName, co.name AS countryName, co.id AS countryId,
            (SELECT COUNT(*) FROM route_stops s WHERE s.routeId = r.id AND s.deleted = 0) AS stopCount
        FROM routes r
        JOIN cities c ON c.id = r.cityId
        JOIN countries co ON co.id = c.countryId
        WHERE r.deleted = 0 AND c.deleted = 0 AND co.deleted = 0
        ORDER BY r.completed, r.createdAt DESC
        """,
    )
    fun observeRows(): Flow<List<RouteRow>>

    /** The routes through one country's cities, for picking one onto a trip day. */
    @Query(
        """
        SELECT r.*, c.name AS cityName, co.name AS countryName, co.id AS countryId,
            (SELECT COUNT(*) FROM route_stops s WHERE s.routeId = r.id AND s.deleted = 0) AS stopCount
        FROM routes r
        JOIN cities c ON c.id = r.cityId
        JOIN countries co ON co.id = c.countryId
        WHERE r.deleted = 0 AND c.deleted = 0 AND co.id = :countryId
        ORDER BY r.completed, r.createdAt DESC
        """,
    )
    fun observeRowsInCountry(countryId: String): Flow<List<RouteRow>>

    @Query("SELECT * FROM routes WHERE id = :id AND deleted = 0")
    fun observeById(id: String): Flow<RouteEntity?>

    @Query("SELECT * FROM routes WHERE dirty = 1 ORDER BY updatedAt LIMIT :limit")
    override suspend fun dirty(limit: Int): List<RouteEntity>

    @Query("SELECT * FROM routes WHERE id = :id")
    override suspend fun find(id: String): RouteEntity?

    @Upsert
    override suspend fun upsert(entities: List<RouteEntity>)

    @Query("UPDATE routes SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt")
    override suspend fun clearDirty(id: String, updatedAt: Long)
}
