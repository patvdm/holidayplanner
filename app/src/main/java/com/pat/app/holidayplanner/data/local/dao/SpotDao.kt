package com.pat.app.holidayplanner.data.local.dao

import com.pat.app.holidayplanner.domain.Priority
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

    /**
     * Every spot in one country's cities, with its link counts. The country screen groups them under
     * their cities itself.
     *
     * Links are counted twice over: everything attached, which is what a delete takes with it, and
     * the links proper, which is what someone filtering for them means.
     */
    @Query(
        """
        SELECT s.*,
            (SELECT COUNT(*) FROM links l WHERE l.spotId = s.id AND l.deleted = 0) AS linkCount,
            (SELECT COUNT(*) FROM links w WHERE w.spotId = s.id AND w.deleted = 0 AND w.source <> 'PHOTO')
                AS webLinkCount
        FROM spots s
        WHERE s.deleted = 0 AND (
            s.parentCountryId = :countryId
            OR s.parentCityId IN (SELECT id FROM cities WHERE countryId = :countryId AND deleted = 0)
        )
        ORDER BY s.sortKey, s.id
        """,
    )
    fun observeRowsInCountry(countryId: String): Flow<List<SpotRow>>

    /** Every spot anywhere, with where it is, for the Spots tab. */
    @Query(
        """
        SELECT s.*, COALESCE(p.name, '') AS cityName, c.id AS countryId, c.name AS countryName,
            c.isoCode AS isoCode
        FROM spots s
        LEFT JOIN cities p ON p.id = s.parentCityId
        JOIN countries c ON c.id = CASE WHEN s.parentCityId = '' THEN s.parentCountryId ELSE p.countryId END
        WHERE s.deleted = 0 AND (s.parentCityId = '' OR p.deleted = 0) AND c.deleted = 0
        """,
    )
    fun observeAllRows(): Flow<List<SpotListRow>>

    /** Every spot with the hierarchy above it, for searching and grouping in the picker. */
    @Query(
        """
        SELECT s.id AS spotId, s.name AS spotName, s.parentCityId AS cityId, COALESCE(p.name, '') AS cityName,
            c.id AS countryId, c.name AS countryName, c.isoCode AS isoCode
        FROM spots s
        LEFT JOIN cities p ON p.id = s.parentCityId
        JOIN countries c ON c.id = CASE WHEN s.parentCityId = '' THEN s.parentCountryId ELSE p.countryId END
        WHERE s.deleted = 0 AND (s.parentCityId = '' OR p.deleted = 0) AND c.deleted = 0
        ORDER BY c.name COLLATE NOCASE, COALESCE(p.name, '') COLLATE NOCASE, s.name COLLATE NOCASE
        """,
    )
    fun observePickerRows(): Flow<List<SpotPickerRow>>

    /** The place and country a spot belongs to. */
    @Query(
        """
        SELECT s.parentCityId AS cityId, COALESCE(p.name, '') AS cityName, c.id AS countryId,
            c.name AS countryName, c.isoCode AS isoCode
        FROM spots s
        LEFT JOIN cities p ON p.id = s.parentCityId
        JOIN countries c ON c.id = CASE WHEN s.parentCityId = '' THEN s.parentCountryId ELSE p.countryId END
        WHERE s.id = :spotId
        """,
    )
    fun observeContext(spotId: String): Flow<SpotContext?>

    /**
     * Spots whose picture is exactly [imageUrl], with where they are so the search can say so:
     * blank for spots nobody has searched for yet, NO_PICTURE for ones a search came back empty.
     */
    @Query(
        """
        SELECT s.*, COALESCE(p.name, '') AS cityName, c.id AS countryId, c.name AS countryName,
            c.isoCode AS isoCode
        FROM spots s
        LEFT JOIN cities p ON p.id = s.parentCityId
        JOIN countries c ON c.id = CASE WHEN s.parentCityId = '' THEN s.parentCountryId ELSE p.countryId END
        WHERE s.deleted = 0 AND (s.parentCityId = '' OR p.deleted = 0) AND c.deleted = 0 AND s.imageUrl = :imageUrl
        ORDER BY s.createdAt DESC
        """,
    )
    fun observeWithImageUrl(imageUrl: String): Flow<List<SpotListRow>>

    /** Spots that carry a Maps link but no coordinates, which the map cannot plot. */
    @Query(
        """
        SELECT * FROM spots
        WHERE deleted = 0 AND mapsUrl != '' AND (lat IS NULL OR lng IS NULL)
        """,
    )
    suspend fun withLinkButNoCoordinates(): List<SpotEntity>

    /** The spots of one city, for looking up all their opening hours at once. */
    @Query("SELECT * FROM spots WHERE deleted = 0 AND parentCityId = :cityId")
    suspend fun inCity(cityId: String): List<SpotEntity>

    /** The spots of one trip: those planned on a day, and the stops of the routes walked on it. */
    @Query(
        """
        SELECT * FROM spots WHERE deleted = 0 AND id IN (
            SELECT spotId FROM trip_items WHERE tripId = :tripId AND deleted = 0 AND spotId != ''
            UNION
            SELECT st.spotId FROM route_stops st
            JOIN trip_items i ON i.routeId = st.routeId AND i.tripId = :tripId AND i.deleted = 0
            WHERE st.deleted = 0
        )
        """,
    )
    suspend fun onTrip(tripId: String): List<SpotEntity>

    /** Every spot we know the position of. The map shows them all and only chooses where to look. */
    @Query(
        """
        SELECT s.id AS spotId, s.name AS name, s.lat AS lat, s.lng AS lng, s.status AS status,
            s.priority AS priority, s.type AS type, s.marks AS marks, s.openingHours AS openingHours,
            s.parentCityId AS cityId, COALESCE(p.name, '') AS cityName, c.id AS countryId,
            c.name AS countryName
        FROM spots s
        LEFT JOIN cities p ON p.id = s.parentCityId
        JOIN countries c ON c.id = CASE WHEN s.parentCityId = '' THEN s.parentCountryId ELSE p.countryId END
        WHERE s.deleted = 0 AND (s.parentCityId = '' OR p.deleted = 0) AND c.deleted = 0
            AND s.lat IS NOT NULL AND s.lng IS NOT NULL
        """,
    )
    fun observePins(): Flow<List<SpotPin>>

    /**
     * The first key among the spots of [priority] in a city, or among a country's own spots when
     * [cityId] is empty, leaving out [excluding]: where the top of that group is.
     */
    @Query(
        """
        SELECT sortKey FROM spots
        WHERE parentCityId = :cityId AND parentCountryId = :countryId AND deleted = 0
            AND priority = :priority AND id != :excluding
        ORDER BY sortKey, id LIMIT 1
        """,
    )
    suspend fun firstSortKey(cityId: String, countryId: String, priority: Priority, excluding: String = ""): String?

    /** The last spot in a city, or among a country's own spots when [cityId] is empty. */
    @Query(
        """
        SELECT sortKey FROM spots
        WHERE parentCityId = :cityId AND parentCountryId = :countryId AND deleted = 0
        ORDER BY sortKey DESC, id DESC LIMIT 1
        """,
    )
    suspend fun lastSortKey(cityId: String, countryId: String): String?

    @Query("SELECT * FROM spots WHERE dirty = 1 ORDER BY updatedAt LIMIT :limit")
    override suspend fun dirty(limit: Int): List<SpotEntity>

    @Query("SELECT * FROM spots WHERE id = :id")
    override suspend fun find(id: String): SpotEntity?

    @Upsert
    override suspend fun upsert(entities: List<SpotEntity>)

    @Query("UPDATE spots SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt")
    override suspend fun clearDirty(id: String, updatedAt: Long)
}
