package com.pat.app.holidayplanner.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.pat.app.holidayplanner.data.local.LinkEntity
import com.pat.app.holidayplanner.data.local.SyncDao
import kotlinx.coroutines.flow.Flow

@Dao
interface LinkDao : SyncDao<LinkEntity> {

    @Query("SELECT * FROM links WHERE spotId = :spotId AND deleted = 0 ORDER BY createdAt DESC")
    fun observeBySpot(spotId: String): Flow<List<LinkEntity>>

    /**
     * Every link and photo in one country, whatever it hangs on: the country itself, one of its
     * cities, or a spot in one of them. The country screen shows all three, so it asks once.
     */
    @Query(
        """
        SELECT * FROM links
        WHERE deleted = 0 AND (
            countryId = :countryId
            OR cityId IN (SELECT id FROM cities WHERE countryId = :countryId AND deleted = 0)
            OR spotId IN (
                SELECT s.id FROM spots s JOIN cities c ON c.id = s.parentCityId
                WHERE c.countryId = :countryId AND s.deleted = 0 AND c.deleted = 0
            )
        )
        ORDER BY createdAt
        """,
    )
    fun observeInCountry(countryId: String): Flow<List<LinkEntity>>

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
