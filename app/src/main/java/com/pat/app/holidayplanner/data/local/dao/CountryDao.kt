package com.pat.app.holidayplanner.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.pat.app.holidayplanner.data.local.CountryEntity
import com.pat.app.holidayplanner.data.local.CountryRow
import com.pat.app.holidayplanner.data.local.SyncDao
import kotlinx.coroutines.flow.Flow

@Dao
interface CountryDao : SyncDao<CountryEntity> {

    @Query("SELECT * FROM countries WHERE deleted = 0 ORDER BY sortKey, id")
    fun observeAll(): Flow<List<CountryEntity>>

    /** Countries with the city and spot counts their rows show. */
    @Query(
        """
        SELECT c.*,
            (SELECT COUNT(*) FROM cities p WHERE p.countryId = c.id AND p.deleted = 0) AS cityCount,
            (SELECT COUNT(*) FROM spots s WHERE s.deleted = 0 AND s.parentCityId IN
                (SELECT p2.id FROM cities p2 WHERE p2.countryId = c.id AND p2.deleted = 0)) AS spotCount
        FROM countries c
        WHERE c.deleted = 0
        ORDER BY c.sortKey, c.id
        """,
    )
    fun observeRows(): Flow<List<CountryRow>>

    @Query("SELECT * FROM countries WHERE id = :id AND deleted = 0")
    fun observeById(id: String): Flow<CountryEntity?>

    @Query("SELECT sortKey FROM countries WHERE deleted = 0 ORDER BY sortKey DESC, id DESC LIMIT 1")
    suspend fun lastSortKey(): String?

    /** Countries are identified by their ISO code, so a picked city lands in the one that exists. */
    @Query("SELECT * FROM countries WHERE isoCode = :isoCode AND deleted = 0 LIMIT 1")
    suspend fun findByIsoCode(isoCode: String): CountryEntity?

    @Query("SELECT COUNT(*) FROM cities WHERE countryId = :countryId AND deleted = 0")
    suspend fun countCities(countryId: String): Int

    @Query(
        """
        SELECT COUNT(*) FROM spots
        WHERE deleted = 0 AND parentCityId IN (SELECT id FROM cities WHERE countryId = :countryId AND deleted = 0)
        """,
    )
    suspend fun countSpots(countryId: String): Int

    @Query("SELECT * FROM countries WHERE dirty = 1 ORDER BY updatedAt LIMIT :limit")
    override suspend fun dirty(limit: Int): List<CountryEntity>

    @Query("SELECT * FROM countries WHERE id = :id")
    override suspend fun find(id: String): CountryEntity?

    @Upsert
    override suspend fun upsert(entities: List<CountryEntity>)

    @Query("UPDATE countries SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt")
    override suspend fun clearDirty(id: String, updatedAt: Long)
}
