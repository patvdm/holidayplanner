package com.pat.app.holidayplanner.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.pat.app.holidayplanner.data.local.SyncDao
import com.pat.app.holidayplanner.data.local.TripItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TripItemDao : SyncDao<TripItemEntity> {

    @Query("SELECT * FROM trip_items WHERE tripId = :tripId AND deleted = 0 ORDER BY startLocal, title")
    fun observeByTrip(tripId: String): Flow<List<TripItemEntity>>

    @Query("SELECT * FROM trip_items WHERE id = :id AND deleted = 0")
    fun observeById(id: String): Flow<TripItemEntity?>

    /** The zone last used on this trip, so a new item starts where the previous one left off. */
    @Query(
        """
        SELECT startTimeZone FROM trip_items
        WHERE tripId = :tripId AND deleted = 0 AND startTimeZone != ''
        ORDER BY updatedAt DESC LIMIT 1
        """,
    )
    suspend fun lastTimeZone(tripId: String): String?

    @Query("SELECT * FROM trip_items WHERE dirty = 1 ORDER BY updatedAt LIMIT :limit")
    override suspend fun dirty(limit: Int): List<TripItemEntity>

    @Query("SELECT * FROM trip_items WHERE id = :id")
    override suspend fun find(id: String): TripItemEntity?

    @Upsert
    override suspend fun upsert(entities: List<TripItemEntity>)

    @Query("UPDATE trip_items SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt")
    override suspend fun clearDirty(id: String, updatedAt: Long)
}
