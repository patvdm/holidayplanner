package com.pat.app.holidayplanner.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.pat.app.holidayplanner.data.local.SpotContext
import com.pat.app.holidayplanner.data.local.SpotEntity
import com.pat.app.holidayplanner.data.local.SpotListRow
import com.pat.app.holidayplanner.data.local.SpotPickerRow
import com.pat.app.holidayplanner.data.local.SpotPin
import com.pat.app.holidayplanner.data.local.SpotRow
import com.pat.app.holidayplanner.data.local.SyncDao
import kotlinx.coroutines.flow.Flow

@Dao
interface SpotDao : SyncDao<SpotEntity> {

    @Query("SELECT * FROM spots WHERE parentCityId = :cityId AND deleted = 0 ORDER BY sortKey, id")
    fun observeByPlace(cityId: String): Flow<List<SpotEntity>>

    @Query("SELECT * FROM spots WHERE id = :id AND deleted = 0")
    fun observeById(id: String): Flow<SpotEntity?>

    @Query("SELECT * FROM spots WHERE deleted = 0 ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<SpotEntity>>

    /** Spots with their heart and link counts, and whether the signed-in person hearted them. */
    @Query(
        """
        SELECT s.*,
            (SELECT COUNT(*) FROM hearts h WHERE h.spotId = s.id AND h.deleted = 0) AS heartCount,
            (SELECT COUNT(*) FROM links l WHERE l.spotId = s.id AND l.deleted = 0) AS linkCount,
            (SELECT COUNT(*) FROM hearts m WHERE m.spotId = s.id AND m.userId = :userId AND m.deleted = 0) > 0
                AS heartedByMe
        FROM spots s
        WHERE s.parentCityId = :cityId AND s.deleted = 0
        ORDER BY s.sortKey, s.id
        """,
    )
    fun observeRows(cityId: String, userId: String): Flow<List<SpotRow>>

    /** Every spot anywhere, with where it is, for the Spots tab. */
    @Query(
        """
        SELECT s.*, p.name AS cityName, c.id AS countryId, c.name AS countryName, c.isoCode AS isoCode
        FROM spots s
        JOIN cities p ON p.id = s.parentCityId
        JOIN countries c ON c.id = p.countryId
        WHERE s.deleted = 0 AND p.deleted = 0 AND c.deleted = 0
        """,
    )
    fun observeAllRows(): Flow<List<SpotListRow>>

    /** Every spot with the hierarchy above it, for searching and grouping in the picker. */
    @Query(
        """
        SELECT s.id AS spotId, s.name AS spotName, p.id AS cityId, p.name AS cityName,
            c.name AS countryName, c.isoCode AS isoCode
        FROM spots s
        JOIN cities p ON p.id = s.parentCityId
        JOIN countries c ON c.id = p.countryId
        WHERE s.deleted = 0 AND p.deleted = 0 AND c.deleted = 0
        ORDER BY c.name COLLATE NOCASE, p.name COLLATE NOCASE, s.name COLLATE NOCASE
        """,
    )
    fun observePickerRows(): Flow<List<SpotPickerRow>>

    /** The place and country a spot belongs to. */
    @Query(
        """
        SELECT p.id AS cityId, p.name AS cityName, c.id AS countryId, c.name AS countryName, c.isoCode AS isoCode
        FROM spots s
        JOIN cities p ON p.id = s.parentCityId
        JOIN countries c ON c.id = p.countryId
        WHERE s.id = :spotId
        """,
    )
    fun observeContext(spotId: String): Flow<SpotContext?>

    /** Spots that carry a Maps link but no coordinates, which the map cannot plot. */
    @Query(
        """
        SELECT * FROM spots
        WHERE deleted = 0 AND mapsUrl != '' AND (lat IS NULL OR lng IS NULL)
        """,
    )
    suspend fun withLinkButNoCoordinates(): List<SpotEntity>

    /** Every spot we know the position of. The map shows them all and only chooses where to look. */
    @Query(
        """
        SELECT s.id AS spotId, s.name AS name, s.lat AS lat, s.lng AS lng, s.status AS status,
            ci.id AS cityId, ci.name AS cityName, co.id AS countryId, co.name AS countryName
        FROM spots s
        JOIN cities ci ON ci.id = s.parentCityId
        JOIN countries co ON co.id = ci.countryId
        WHERE s.deleted = 0 AND ci.deleted = 0 AND co.deleted = 0
            AND s.lat IS NOT NULL AND s.lng IS NOT NULL
        """,
    )
    fun observePins(): Flow<List<SpotPin>>

    @Query(
        """
        SELECT sortKey FROM spots
        WHERE parentCityId = :cityId AND deleted = 0
        ORDER BY sortKey DESC, id DESC LIMIT 1
        """,
    )
    suspend fun lastSortKey(cityId: String): String?

    @Query("SELECT * FROM spots WHERE dirty = 1 ORDER BY updatedAt LIMIT :limit")
    override suspend fun dirty(limit: Int): List<SpotEntity>

    @Query("SELECT * FROM spots WHERE id = :id")
    override suspend fun find(id: String): SpotEntity?

    @Upsert
    override suspend fun upsert(entities: List<SpotEntity>)

    @Query("UPDATE spots SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt")
    override suspend fun clearDirty(id: String, updatedAt: Long)
}
