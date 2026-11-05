package com.pat.app.holidayplanner.data.repo

import com.pat.app.holidayplanner.data.local.dao.CityDao
import com.pat.app.holidayplanner.data.local.dao.SpotDao
import com.pat.app.holidayplanner.domain.SortKey

/**
 * Brings a city to the top of its priority group when one of its spots is added, edited by hand,
 * or moved into it, so the cities being worked on are the ones at the top. Ticking a spot visited
 * and the app's own fills (pictures, coordinates, opening hours) do not count. A city already at
 * the top is left alone, so it costs nothing to call this after every edit.
 */
class CityLift(
    private val spotDao: SpotDao,
    private val cityDao: CityDao,
    private val writer: LocalWriter,
) {

    /** Lifts the city [spotId] is in, if it is in one rather than filed on its country. */
    suspend fun ofSpot(spotId: String) {
        spotDao.find(spotId)?.parentCityId?.takeIf { it.isNotEmpty() }?.let { city(it) }
    }

    suspend fun city(cityId: String) {
        val city = cityDao.find(cityId)?.takeUnless { it.meta.deleted } ?: return
        val first = cityDao.firstSortKey(city.countryId, city.priority, excluding = cityId) ?: return
        if (city.sortKey < first) return
        val stamp = writer.stamp()
        cityDao.upsert(
            listOf(city.copy(sortKey = SortKey.before(first), meta = city.meta.touched(stamp.now, stamp.userId))),
        )
        writer.requestSync()
    }
}
