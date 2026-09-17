package com.pat.app.holidayplanner.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.pat.app.holidayplanner.data.local.CityEntity
import com.pat.app.holidayplanner.data.local.CityOption
import com.pat.app.holidayplanner.data.local.CityRow
import com.pat.app.holidayplanner.data.local.SyncDao
import kotlinx.coroutines.flow.Flow

@Dao
interface CityDao : SyncDao<CityEntity> {

    @Query("SELECT * FROM cities WHERE countryId = :countryId AND deleted = 0 ORDER BY sortKey, id")
    fun observeByCountry(countryId: String): Flow<List<CityEntity>>

    @Query("SELECT * FROM cities WHERE id = :id AND deleted = 0")
    fun observeById(id: String): Flow<CityEntity?>

    /** Places with the number of spots under each. */
    @Query(
        """
        SELECT p.*,
            (SELECT COUNT(*) FROM spots s WHERE s.parentCityId = p.id AND s.deleted = 0) AS spotCount
        FROM cities p
        WHERE p.countryId = :countryId AND p.deleted = 0
        ORDER BY p.sortKey, p.id
        """,
    )
    fun observeRows(countryId: String): Flow<List<CityRow>>

    /** Every city, labelled with its country: for moving a spot, and for choosing a trip's city. */
    @Query(
        """
        SELECT p.id AS cityId, p.name AS cityName, c.id AS countryId, c.name AS countryName
        FROM cities p JOIN countries c ON c.id = p.countryId
        WHERE p.deleted = 0 AND c.deleted = 0
        ORDER BY c.name COLLATE NOCASE, p.name COLLATE NOCASE
        """,
    )
    fun observeOptions(): Flow<List<CityOption>>

    @Query(
        """
        SELECT sortKey FROM cities
        WHERE countryId = :countryId AND deleted = 0
        ORDER BY sortKey DESC, id DESC LIMIT 1
        """,
    )
    suspend fun lastSortKey(countryId: String): String?

    @Query(
        """
        SELECT * FROM cities
        WHERE countryId = :countryId AND deleted = 0 AND name = :name COLLATE NOCASE
        LIMIT 1
        """,
    )
    suspend fun findByName(countryId: String, name: String): CityEntity?

    @Query("SELECT COUNT(*) FROM spots WHERE parentCityId = :cityId AND deleted = 0")
    suspend fun countSpots(cityId: String): Int

    @Query("SELECT * FROM cities WHERE dirty = 1 ORDER BY updatedAt LIMIT :limit")
    override suspend fun dirty(limit: Int): List<CityEntity>

    @Query("SELECT * FROM cities WHERE id = :id")
    override suspend fun find(id: String): CityEntity?

    @Upsert
    override suspend fun upsert(entities: List<CityEntity>)

    @Query("UPDATE cities SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt")
    override suspend fun clearDirty(id: String, updatedAt: Long)
}
