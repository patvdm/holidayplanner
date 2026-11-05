package com.pat.app.holidayplanner.data.repo

import com.pat.app.holidayplanner.data.local.HeartEntity
import com.pat.app.holidayplanner.data.local.HeartRow
import com.pat.app.holidayplanner.data.local.SpotContext
import com.pat.app.holidayplanner.data.local.SpotEntity
import com.pat.app.holidayplanner.data.local.SpotHome
import com.pat.app.holidayplanner.data.local.SpotListRow
import com.pat.app.holidayplanner.data.local.SpotPickerRow
import com.pat.app.holidayplanner.data.local.SpotRow
import com.pat.app.holidayplanner.data.local.SyncMeta
import com.pat.app.holidayplanner.data.local.dao.CascadeDao
import com.pat.app.holidayplanner.data.local.dao.HeartDao
import com.pat.app.holidayplanner.data.local.dao.SpotDao
import com.pat.app.holidayplanner.data.sync.Transactor
import com.pat.app.holidayplanner.domain.Priority
import com.pat.app.holidayplanner.domain.SortKey
import com.pat.app.holidayplanner.domain.SpotMark
import com.pat.app.holidayplanner.domain.SpotStatus
import com.pat.app.holidayplanner.domain.withMark
import com.pat.app.holidayplanner.domain.SpotType
import com.pat.app.holidayplanner.domain.heartId
import com.pat.app.holidayplanner.domain.newId
import kotlinx.coroutines.flow.Flow

class SpotRepository(
    private val dao: SpotDao,
    private val heartDao: HeartDao,
    private val cascadeDao: CascadeDao,
    private val transactor: Transactor,
    private val writer: LocalWriter,
) {

    fun observeSpots(cityId: String): Flow<List<SpotEntity>> = dao.observeByPlace(cityId)

    fun observeSpot(id: String): Flow<SpotEntity?> = dao.observeById(id)

    /** Who hearted this spot, by name. */
    fun observeHeartNames(spotId: String): Flow<List<HeartRow>> = heartDao.observeNames(spotId)

    /** Every spot in a country, with its heart and link counts, as the country screen shows them. */
    fun observeRowsInCountry(countryId: String, userId: String): Flow<List<SpotRow>> =
        dao.observeRowsInCountry(countryId, userId)

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
            sortKey = SortKey.after(dao.lastSortKey(cityId, if (cityId.isEmpty()) countryId else "")),
            priority = Priority.NORMAL,
            imageUrl = "",
        )
        dao.upsert(listOf(spot))
        writer.requestSync()
        return id
    }

    suspend fun rename(id: String, name: String) = edit(id) { it.copy(name = name.trim()) }

    suspend fun setNotes(id: String, notes: String) = edit(id) { it.copy(notes = notes) }

    /** A spot holds one Maps link; setting a new one replaces whatever was there. */
    suspend fun setMapsUrl(id: String, mapsUrl: String) = edit(id) { it.copy(mapsUrl = mapsUrl.trim()) }

    suspend fun setAddress(id: String, address: String) = edit(id) { it.copy(address = address.trim()) }

    suspend fun setStatus(id: String, status: SpotStatus) = edit(id) { it.copy(status = status) }

    /** Turns one mark on or off, leaving any others the spot carries alone. */
    suspend fun setMark(id: String, mark: SpotMark, on: Boolean) =
        edit(id) { it.copy(marks = it.marks.withMark(mark, on)) }

    /** Null clears the type, since saying nothing is a valid answer. */
    suspend fun setType(id: String, type: SpotType) = edit(id) { it.copy(type = type) }

    suspend fun setPriority(id: String, priority: Priority) = edit(id) { it.copy(priority = priority) }

    /** Blank asks for a picture to be found again; anything else is kept as given. */
    suspend fun setImageUrl(id: String, url: String) = edit(id) { it.copy(imageUrl = url.trim()) }

    suspend fun setOpeningHours(id: String, hours: String) = edit(id) { it.copy(openingHours = hours) }

    /** Spots whose picture is exactly [imageUrl]: blank for unsearched, NO_PICTURE for searched in vain. */
    fun observeWithImageUrl(imageUrl: String): Flow<List<SpotListRow>> = dao.observeWithImageUrl(imageUrl)

    suspend fun move(id: String, previousKey: String?, nextKey: String?) =
        edit(id) { it.copy(sortKey = SortKey.between(previousKey, nextKey)) }

    /**
     * Files a spot under a city, or straight under a country when [home] names no city, appending it
     * to the spots already there.
     */
    suspend fun moveTo(id: String, home: SpotHome) = edit(id) {
        val countryId = if (home.isCountry) home.countryId else ""
        it.copy(
            parentCityId = home.cityId,
            parentCountryId = countryId,
            sortKey = SortKey.after(dao.lastSortKey(home.cityId, countryId)),
        )
    }

    /**
     * Hearts use a deterministic id, so un-hearting only flips `deleted` and hearting again flips it
     * back. That keeps one row per person per spot however often they change their mind.
     */
    suspend fun toggleHeart(spotId: String) {
        val stamp = writer.stamp()
        if (stamp.userId.isEmpty()) return
        val id = heartId(spotId, stamp.userId)
        val existing = heartDao.find(id)
        val heart = existing?.copy(
            meta = existing.meta.touched(stamp.now, stamp.userId, deleted = !existing.meta.deleted),
        ) ?: HeartEntity(
            meta = SyncMeta.created(id, stamp.now, stamp.userId),
            spotId = spotId,
            userId = stamp.userId,
        )
        heartDao.upsert(listOf(heart))
        writer.requestSync()
    }

    suspend fun delete(id: String) {
        val stamp = writer.stamp()
        transactor.transaction {
            cascadeDao.deleteStopsOfSpot(id, stamp.now, stamp.userId)
            cascadeDao.deleteHeartsOfSpot(id, stamp.now, stamp.userId)
            cascadeDao.deleteLinksOfSpot(id, stamp.now, stamp.userId)
            cascadeDao.deleteSpot(id, stamp.now, stamp.userId)
        }
        writer.requestSync()
    }

    private suspend fun edit(id: String, change: suspend (SpotEntity) -> SpotEntity) {
        val current = dao.find(id) ?: return
        val stamp = writer.stamp()
        val updated = change(current)
        dao.upsert(listOf(updated.copy(meta = updated.meta.touched(stamp.now, stamp.userId))))
        writer.requestSync()
    }
}
