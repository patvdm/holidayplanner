package com.pat.app.holidayplanner.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.pat.app.holidayplanner.data.local.SyncDao
import com.pat.app.holidayplanner.data.local.TripEntity
import com.pat.app.holidayplanner.data.local.TripRow
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao : SyncDao<TripEntity> {

    @Query("SELECT * FROM trips WHERE deleted = 0 ORDER BY startDate, name COLLATE NOCASE")
    fun observeAll(): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE id = :id AND deleted = 0")
    fun observeById(id: String): Flow<TripEntity?>

    /** Every trip with the country it is to, as the list shows them. */
    @Query(
        """
        SELECT t.*, c.name AS countryName, c.isoCode AS isoCode
        FROM trips t LEFT JOIN countries c ON c.id = t.countryId AND c.deleted = 0
        WHERE t.deleted = 0
        ORDER BY t.startDate, t.name COLLATE NOCASE
        """,
    )
    fun observeRows(): Flow<List<TripRow>>

    /** The country a trip is to, for a search that should stay inside it. */
    @Query(
        """
        SELECT c.isoCode FROM trips t
        JOIN countries c ON c.id = t.countryId
        WHERE t.id = :tripId AND t.deleted = 0
        """,
    )
    suspend fun countryIso(tripId: String): String?

    @Query("SELECT COUNT(*) FROM trip_items WHERE tripId = :tripId AND deleted = 0")
    suspend fun countItems(tripId: String): Int

    @Query("SELECT * FROM trips WHERE dirty = 1 ORDER BY updatedAt LIMIT :limit")
    override suspend fun dirty(limit: Int): List<TripEntity>

    @Query("SELECT * FROM trips WHERE id = :id")
    override suspend fun find(id: String): TripEntity?

    @Upsert
    override suspend fun upsert(entities: List<TripEntity>)

    @Query("UPDATE trips SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt")
    override suspend fun clearDirty(id: String, updatedAt: Long)
}
