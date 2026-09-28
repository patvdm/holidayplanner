package com.pat.app.holidayplanner.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import com.pat.app.holidayplanner.data.local.SearchHit
import kotlinx.coroutines.flow.Flow

/**
 * Global search across everything, offline.
 *
 * One UNION over the real tables rather than a shadow index. At this app's size a scan is instant,
 * and reading the source of truth means search can never disagree with what is on screen, which a
 * separately maintained index eventually does.
 */
@Dao
interface SearchDao {

    @Query(
        """
        SELECT 'COUNTRY' AS type, c.id AS id, c.id AS navId, c.name AS title, '' AS subtitle
        FROM countries c
        WHERE c.deleted = 0 AND c.name LIKE :like ESCAPE '\'

        UNION ALL
        SELECT 'CITY', p.id, p.countryId, p.name, co.name
        FROM cities p JOIN countries co ON co.id = p.countryId
        WHERE p.deleted = 0 AND co.deleted = 0 AND p.name LIKE :like ESCAPE '\'

        UNION ALL
        SELECT 'SPOT', s.id, s.id, s.name, COALESCE(ci.name, co.name)
        FROM spots s
        LEFT JOIN cities ci ON ci.id = s.parentCityId
        JOIN countries co ON co.id = CASE WHEN s.parentCityId = '' THEN s.parentCountryId ELSE ci.countryId END
        WHERE s.deleted = 0 AND (s.parentCityId = '' OR ci.deleted = 0) AND co.deleted = 0 AND (
            s.name LIKE :like ESCAPE '\' OR s.notes LIKE :like ESCAPE '\'
            OR s.address LIKE :like ESCAPE '\'
        )

        UNION ALL
        SELECT 'LINK', l.id, l.spotId, l.note, l.url
        FROM links l
        WHERE l.deleted = 0 AND l.spotId != '' AND l.note != '' AND l.note LIKE :like ESCAPE '\'

        UNION ALL
        SELECT 'TRIP', t.id, t.id, t.name, t.startDate
        FROM trips t
        WHERE t.deleted = 0 AND (t.name LIKE :like ESCAPE '\' OR t.notes LIKE :like ESCAPE '\')

        UNION ALL
        SELECT 'TRIP_ITEM', i.id, i.id, i.title, tr.name
        FROM trip_items i JOIN trips tr ON tr.id = i.tripId
        WHERE i.deleted = 0 AND tr.deleted = 0 AND (
            i.title LIKE :like ESCAPE '\' OR i.bookingRef LIKE :like ESCAPE '\'
            OR i.notes LIKE :like ESCAPE '\'
        )

        ORDER BY type, title COLLATE NOCASE
        """,
    )
    fun search(like: String): Flow<List<SearchHit>>
}
