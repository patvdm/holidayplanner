package com.pat.app.holidayplanner.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.pat.app.holidayplanner.data.local.SyncStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncStateDao {

    @Query("SELECT lastServerTime FROM sync_state WHERE id = 1")
    suspend fun lastServerTime(): Long?

    @Upsert
    suspend fun upsert(state: SyncStateEntity)

    /** How many rows across all tables are still waiting to be pushed. */
    @Query(
        """
        SELECT SUM(pending) FROM (
            SELECT COUNT(*) AS pending FROM users WHERE dirty = 1
            UNION ALL SELECT COUNT(*) FROM countries WHERE dirty = 1
            UNION ALL SELECT COUNT(*) FROM cities WHERE dirty = 1
            UNION ALL SELECT COUNT(*) FROM spots WHERE dirty = 1
            UNION ALL SELECT COUNT(*) FROM links WHERE dirty = 1
            UNION ALL SELECT COUNT(*) FROM hearts WHERE dirty = 1
            UNION ALL SELECT COUNT(*) FROM trips WHERE dirty = 1
            UNION ALL SELECT COUNT(*) FROM trip_items WHERE dirty = 1
            UNION ALL SELECT COUNT(*) FROM routes WHERE dirty = 1
            UNION ALL SELECT COUNT(*) FROM route_stops WHERE dirty = 1
        )
        """,
    )
    fun observePendingCount(): Flow<Int>
}
