package com.pat.app.holidayplanner.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.pat.app.holidayplanner.data.local.InboxRow
import com.pat.app.holidayplanner.data.local.LinkEntity
import com.pat.app.holidayplanner.data.local.SyncDao
import kotlinx.coroutines.flow.Flow

@Dao
interface LinkDao : SyncDao<LinkEntity> {

    @Query("SELECT * FROM links WHERE spotId = '' AND deleted = 0 ORDER BY createdAt DESC")
    fun observeInbox(): Flow<List<LinkEntity>>

    /** Inbox links with the name of whoever shared them. */
    @Query(
        """
        SELECT l.*, COALESCE(u.name, '') AS authorName
        FROM links l LEFT JOIN users u ON u.id = l.createdBy
        WHERE l.spotId = '' AND l.deleted = 0
        ORDER BY l.createdAt DESC
        """,
    )
    fun observeInboxRows(): Flow<List<InboxRow>>

    @Query("SELECT COUNT(*) FROM links WHERE spotId = '' AND deleted = 0")
    fun observeInboxCount(): Flow<Int>

    @Query("SELECT * FROM links WHERE spotId = :spotId AND deleted = 0 ORDER BY createdAt DESC")
    fun observeBySpot(spotId: String): Flow<List<LinkEntity>>

    @Query("SELECT * FROM links WHERE url = :url AND deleted = 0 LIMIT 1")
    suspend fun findByUrl(url: String): LinkEntity?

    @Query("SELECT * FROM links WHERE dirty = 1 ORDER BY updatedAt LIMIT :limit")
    override suspend fun dirty(limit: Int): List<LinkEntity>

    @Query("SELECT * FROM links WHERE id = :id")
    override suspend fun find(id: String): LinkEntity?

    @Upsert
    override suspend fun upsert(entities: List<LinkEntity>)

    @Query("UPDATE links SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt")
    override suspend fun clearDirty(id: String, updatedAt: Long)
}
