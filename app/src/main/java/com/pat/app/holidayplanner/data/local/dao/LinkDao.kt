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

    /**
     * Every shared link, newest first, with who shared it and where it went.
     *
     * Not just the unfiled ones: a share is now always attached to a spot, so this reads as a list
     * of what has been shared lately rather than a pile of things still to deal with.
     */
    @Query(
        """
        SELECT l.*, COALESCE(u.name, '') AS authorName,
            COALESCE(s.name, '') AS spotName, COALESCE(c.name, '') AS cityName
        FROM links l
        LEFT JOIN users u ON u.id = l.createdBy
        LEFT JOIN spots s ON s.id = l.spotId AND s.deleted = 0
        LEFT JOIN cities c ON c.id = s.parentCityId
        WHERE l.deleted = 0
        ORDER BY l.createdAt DESC
        """,
    )
    fun observeInboxRows(): Flow<List<InboxRow>>

    /** The badge counts only links with nowhere to live, which are the ones needing a decision. */
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
