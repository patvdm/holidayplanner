package com.pat.app.holidayplanner.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import com.pat.app.holidayplanner.data.local.FeedRow
import kotlinx.coroutines.flow.Flow

/**
 * What has been added lately, by anyone.
 *
 * Everything here is derived from createdAt, which every row already carries, so the feed needs no
 * table of its own and cannot drift out of step with the data it describes. It deliberately
 * includes your own additions: with two people the strip is quiet enough that seeing the whole
 * week is more useful than seeing only half of it.
 */
@Dao
interface FeedDao {

    @Query(
        """
        SELECT 'SPOT' AS type, s.id AS id, s.id AS navId, s.name AS title,
            ci.name AS place, s.createdAt AS createdAt, COALESCE(u.name, '') AS authorName
        FROM spots s
        JOIN cities ci ON ci.id = s.parentCityId
        LEFT JOIN users u ON u.id = s.createdBy
        WHERE s.deleted = 0 AND s.createdAt >= :since

        UNION ALL
        SELECT 'LINK', l.id, l.spotId,
            CASE WHEN l.note != '' THEN l.note ELSE l.url END,
            COALESCE(ci.name, ''), l.createdAt, COALESCE(u.name, '')
        FROM links l
        LEFT JOIN spots s ON s.id = l.spotId AND s.deleted = 0
        LEFT JOIN cities ci ON ci.id = s.parentCityId
        LEFT JOIN users u ON u.id = l.createdBy
        WHERE l.deleted = 0 AND l.createdAt >= :since

        UNION ALL
        SELECT 'TRIP', t.id, t.id, t.name, '', t.createdAt, COALESCE(u.name, '')
        FROM trips t
        LEFT JOIN users u ON u.id = t.createdBy
        WHERE t.deleted = 0 AND t.createdAt >= :since

        UNION ALL
        SELECT 'HEART', h.id, h.spotId, s.name, ci.name, h.createdAt, COALESCE(u.name, '')
        FROM hearts h
        JOIN spots s ON s.id = h.spotId AND s.deleted = 0
        JOIN cities ci ON ci.id = s.parentCityId
        LEFT JOIN users u ON u.id = h.createdBy
        WHERE h.deleted = 0 AND h.createdAt >= :since

        ORDER BY createdAt DESC
        """,
    )
    fun observeSince(since: Long): Flow<List<FeedRow>>
}
