package com.pat.app.holidayplanner.data.repo

import com.pat.app.holidayplanner.data.local.SpotPin
import com.pat.app.holidayplanner.data.local.dao.SpotDao
import kotlinx.coroutines.flow.Flow

/**
 * Spots as positions on a map.
 *
 * Separate from SpotRepository because the map and Near me want one narrow thing, coordinates, and
 * have no use for hearts, tags, statuses or any of the editing that repository exists to do.
 */
class SpotPinRepository(private val dao: SpotDao) {

    /** Everything we know the position of, wherever it is. */
    fun observeAll(): Flow<List<SpotPin>> = dao.observePins()
}
