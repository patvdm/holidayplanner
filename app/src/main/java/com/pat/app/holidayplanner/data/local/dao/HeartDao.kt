package com.pat.app.holidayplanner.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.pat.app.holidayplanner.data.local.HeartEntity
import com.pat.app.holidayplanner.data.local.HeartRow
import com.pat.app.holidayplanner.data.local.SyncDao
import kotlinx.coroutines.flow.Flow

@Dao
interface HeartDao : SyncDao<HeartEntity> {

    @Query("SELECT * FROM hearts WHERE spotId = :spotId AND deleted = 0")
    fun observeBySpot(spotId: String): Flow<List<HeartEntity>>

    /** Who hearted this spot, by name. A heart from someone not synced yet shows an empty name. */
    @Query(
        """
        SELECT h.userId AS userId, COALESCE(u.name, '') AS name
        FROM hearts h LEFT JOIN users u ON u.id = h.userId
        WHERE h.spotId = :spotId AND h.deleted = 0
        ORDER BY name COLLATE NOCASE
        """,
    )
    fun observeNames(spotId: String): Flow<List<HeartRow>>

    @Query("SELECT * FROM hearts WHERE dirty = 1 ORDER BY updatedAt LIMIT :limit")
    override suspend fun dirty(limit: Int): List<HeartEntity>

    @Query("SELECT * FROM hearts WHERE id = :id")
    override suspend fun find(id: String): HeartEntity?

    @Upsert
    override suspend fun upsert(entities: List<HeartEntity>)

    @Query("UPDATE hearts SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt")
    override suspend fun clearDirty(id: String, updatedAt: Long)
}
