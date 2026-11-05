package com.pat.app.holidayplanner.data.repo

import com.pat.app.holidayplanner.data.local.SpotContext
import com.pat.app.holidayplanner.data.local.SpotEntity
import com.pat.app.holidayplanner.data.local.SpotHome
import com.pat.app.holidayplanner.data.local.SpotListRow
import com.pat.app.holidayplanner.data.local.SpotPickerRow
import com.pat.app.holidayplanner.data.local.SpotRow
import com.pat.app.holidayplanner.data.local.SyncMeta
import com.pat.app.holidayplanner.data.local.dao.CascadeDao
import com.pat.app.holidayplanner.data.local.dao.SpotDao
import com.pat.app.holidayplanner.data.sync.Transactor
import com.pat.app.holidayplanner.domain.Priority
import com.pat.app.holidayplanner.domain.SortKey
import com.pat.app.holidayplanner.domain.SpotMark
import com.pat.app.holidayplanner.domain.SpotStatus
import com.pat.app.holidayplanner.domain.withMark
import com.pat.app.holidayplanner.domain.SpotType
import com.pat.app.holidayplanner.domain.newId
import kotlinx.coroutines.flow.Flow

/**
 * Spots and every change to them.
 *
 * Order within a city: high priority first, then the rest, each group in the order it was dragged
 * into. A new spot goes to the top of its group, and so does one moved to another city or given
 * the other priority; nothing else moves a spot. Every change made by hand also lifts the spot's
 * city to the top of its own group (see [CityLift]); ticking it visited and the app's own fills do
 * not.
 */
class SpotRepository(
    private val dao: SpotDao,
    private val cascadeDao: CascadeDao,
    private val transactor: Transactor,
    private val writer: LocalWriter,
    private val cityLift: CityLift,
) {

    fun observeSpots(cityId: String): Flow<List<SpotEntity>> = dao.observeByPlace(cityId)

    fun observeSpot(id: String): Flow<SpotEntity?> = dao.observeById(id)

    /** Every spot in a country, with its link counts, as the country screen shows them. */
    fun observeRowsInCountry(countryId: String): Flow<List<SpotRow>> = dao.observeRowsInCountry(countryId)

    /** Every spot anywhere, for the Spots tab. */
    fun observeAllRows(): Flow<List<SpotListRow>> = dao.observeAllRows()

    /** Every spot with its hierarchy, for the picker. */
    fun observePickerRows(): Flow<List<SpotPickerRow>> = dao.observePickerRows()

    /** The place and country a spot sits under. */
    fun observeContext(spotId: String): Flow<SpotContext?> = dao.observeContext(spotId)

    /** Files the spot under [cityId], or straight under [countryId] when no city is given. */
    suspend fun add(
        cityId: String,
        name: String,
        countryId: String = "",
        googlePlaceId: String = "",
        lat: Double? = null,
        lng: Double? = null,
        address: String = "",
        mapsUrl: String = "",
        type: SpotType = SpotType.POI,
    ): String {
        val stamp = writer.stamp()
        val id = newId()
        val home = if (cityId.isEmpty()) countryId else ""
        val spot = SpotEntity(
            meta = SyncMeta.created(id, stamp.now, stamp.userId),
            parentCityId = cityId,
            parentCountryId = if (cityId.isEmpty()) countryId else "",
            name = name.trim(),
            type = type,
            googlePlaceId = googlePlaceId,
            lat = lat,
            lng = lng,
            address = address,
            mapsUrl = mapsUrl,
            notes = "",
            status = SpotStatus.UNVISITED,
            marks = "",
            sortKey = SortKey.before(dao.firstSortKey(cityId, home, Priority.NORMAL)),
            priority = Priority.NORMAL,
            imageUrl = "",
        )
        dao.upsert(listOf(spot))
        writer.requestSync()
        if (cityId.isNotEmpty()) cityLift.city(cityId)
        return id
    }

    suspend fun rename(id: String, name: String) = edit(id, byHand = true) { it.copy(name = name.trim()) }

    suspend fun setNotes(id: String, notes: String) = edit(id, byHand = true) { it.copy(notes = notes) }

    /** A spot holds one Maps link; setting a new one replaces whatever was there. */
    suspend fun setMapsUrl(id: String, mapsUrl: String) =
        edit(id, byHand = true) { it.copy(mapsUrl = mapsUrl.trim()) }

    suspend fun setAddress(id: String, address: String) =
        edit(id, byHand = true) { it.copy(address = address.trim()) }

    /** Ticking a spot visited moves nothing, not even its city. */
    suspend fun setStatus(id: String, status: SpotStatus) = edit(id) { it.copy(status = status) }

    /** Turns one mark on or off, leaving any others the spot carries alone. */
    suspend fun setMark(id: String, mark: SpotMark, on: Boolean) =
        edit(id, byHand = true) { it.copy(marks = it.marks.withMark(mark, on)) }

    suspend fun setType(id: String, type: SpotType) = edit(id, byHand = true) { it.copy(type = type) }

    /** The spot changes group, and lands at the top of the one it joins. */
    suspend fun setPriority(id: String, priority: Priority) = edit(id, byHand = true) {
        if (it.priority == priority) {
            it
        } else {
            it.copy(
                priority = priority,
                sortKey = SortKey.before(dao.firstSortKey(it.parentCityId, it.parentCountryId, priority, id)),
            )
        }
    }

    suspend fun togglePriority(id: String) {
        val current = dao.find(id) ?: return
        setPriority(id, if (current.priority == Priority.HIGH) Priority.NORMAL else Priority.HIGH)
    }

    /** A picture chosen by hand. Blank asks for one to be found again. */
    suspend fun setImageUrl(id: String, url: String) = edit(id, byHand = true) { it.copy(imageUrl = url.trim()) }

    /** A picture the app found by itself, which moves nothing. */
    suspend fun setFoundImageUrl(id: String, url: String) = edit(id) { it.copy(imageUrl = url.trim()) }

    suspend fun setOpeningHours(id: String, hours: String) = edit(id) { it.copy(openingHours = hours) }

    /** Spots whose picture is exactly [imageUrl]: blank for unsearched, NO_PICTURE for searched in vain. */
    fun observeWithImageUrl(imageUrl: String): Flow<List<SpotListRow>> = dao.observeWithImageUrl(imageUrl)

    suspend fun move(id: String, previousKey: String?, nextKey: String?) =
        edit(id) { it.copy(sortKey = SortKey.between(previousKey, nextKey)) }

    /**
     * Files a spot under a city, or straight under a country when [home] names no city, at the top
     * of its group there, as a spot just arrived.
     */
    suspend fun moveTo(id: String, home: SpotHome) = edit(id, byHand = true) {
        val countryId = if (home.isCountry) home.countryId else ""
        it.copy(
            parentCityId = home.cityId,
            parentCountryId = countryId,
            sortKey = SortKey.before(dao.firstSortKey(home.cityId, countryId, it.priority, id)),
        )
    }

    suspend fun delete(id: String) {
        val stamp = writer.stamp()
        transactor.transaction {
            cascadeDao.deleteStopsOfSpot(id, stamp.now, stamp.userId)
            cascadeDao.deleteLinksOfSpot(id, stamp.now, stamp.userId)
            cascadeDao.deleteSpot(id, stamp.now, stamp.userId)
        }
        writer.requestSync()
    }

    /** [byHand] marks a change somebody made, which lifts the spot's city to the top. */
    private suspend fun edit(id: String, byHand: Boolean = false, change: suspend (SpotEntity) -> SpotEntity) {
        val current = dao.find(id) ?: return
        val stamp = writer.stamp()
        val updated = change(current)
        dao.upsert(listOf(updated.copy(meta = updated.meta.touched(stamp.now, stamp.userId))))
        writer.requestSync()
        if (byHand) cityLift.ofSpot(id)
    }
}
