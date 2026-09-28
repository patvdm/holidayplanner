package com.pat.app.holidayplanner.data.repo

import com.pat.app.holidayplanner.data.local.CityEntity
import com.pat.app.holidayplanner.data.local.CityOption
import com.pat.app.holidayplanner.data.local.CityRow
import com.pat.app.holidayplanner.data.local.SpotHome
import com.pat.app.holidayplanner.data.local.SyncMeta
import com.pat.app.holidayplanner.data.local.dao.CascadeDao
import com.pat.app.holidayplanner.data.local.dao.CityDao
import com.pat.app.holidayplanner.data.sync.Transactor
import com.pat.app.holidayplanner.domain.Priority
import com.pat.app.holidayplanner.domain.SortKey
import com.pat.app.holidayplanner.domain.newId
import kotlinx.coroutines.flow.Flow

class CityRepository(
    private val dao: CityDao,
    private val cascadeDao: CascadeDao,
    private val transactor: Transactor,
    private val writer: LocalWriter,
) {

    fun observeCities(countryId: String): Flow<List<CityEntity>> = dao.observeByCountry(countryId)

    fun observeCity(id: String): Flow<CityEntity?> = dao.observeById(id)

    /** Cities with their spot counts, as the country screen shows them. */
    fun observeRows(countryId: String): Flow<List<CityRow>> = dao.observeRows(countryId)

    /** Every city with its country name, for choosing a city. */
    fun observeOptions(): Flow<List<CityOption>> = dao.observeOptions()

    /** Every country and city a spot could be filed under. */
    fun observeHomes(): Flow<List<SpotHome>> = dao.observeHomes()

    suspend fun add(
        countryId: String,
        name: String,
        googlePlaceId: String = "",
        lat: Double? = null,
        lng: Double? = null,
    ): String {
        val stamp = writer.stamp()
        val id = newId()
        val place = CityEntity(
            meta = SyncMeta.created(id, stamp.now, stamp.userId),
            countryId = countryId,
            name = name.trim(),
            googlePlaceId = googlePlaceId,
            lat = lat,
            lng = lng,
            sortKey = SortKey.after(dao.lastSortKey(countryId)),
            priority = Priority.NORMAL,
            imageUrl = "",
            notes = "",
        )
        dao.upsert(listOf(place))
        writer.requestSync()
        return id
    }

    suspend fun find(id: String): CityEntity? = dao.find(id)

    /** Case-insensitive lookup within a country, used when resolving a picked place. */
    suspend fun findByName(countryId: String, name: String): CityEntity? =
        dao.findByName(countryId, name.trim())

    suspend fun rename(id: String, name: String) = edit(id) { it.copy(name = name.trim()) }

    suspend fun setPriority(id: String, priority: Priority) = edit(id) { it.copy(priority = priority) }

    suspend fun setNotes(id: String, notes: String) = edit(id) { it.copy(notes = notes.trim()) }

    /** Blank asks for a picture to be found again; anything else is kept as given. */
    suspend fun setImageUrl(id: String, url: String) = edit(id) { it.copy(imageUrl = url.trim()) }

    fun observeWithoutPicture(): Flow<List<CityOption>> = dao.observeWithoutPicture()

    suspend fun move(id: String, previousKey: String?, nextKey: String?) =
        edit(id) { it.copy(sortKey = SortKey.between(previousKey, nextKey)) }

    suspend fun deleteImpact(id: String): DeleteImpact = DeleteImpact(spots = dao.countSpots(id))

    suspend fun delete(id: String) {
        val stamp = writer.stamp()
        transactor.transaction {
            cascadeDao.deleteStopsOfCity(id, stamp.now, stamp.userId)
            cascadeDao.deleteStopsOfCityRoutes(id, stamp.now, stamp.userId)
            cascadeDao.deleteRoutesOfCity(id, stamp.now, stamp.userId)
            cascadeDao.deleteHeartsOfCity(id, stamp.now, stamp.userId)
            cascadeDao.deleteLinksOfCity(id, stamp.now, stamp.userId)
            cascadeDao.deleteSpotsOfCity(id, stamp.now, stamp.userId)
            cascadeDao.deleteCity(id, stamp.now, stamp.userId)
        }
        writer.requestSync()
    }

    private suspend fun edit(id: String, change: (CityEntity) -> CityEntity) {
        val current = dao.find(id) ?: return
        val stamp = writer.stamp()
        val updated = change(current)
        dao.upsert(listOf(updated.copy(meta = updated.meta.touched(stamp.now, stamp.userId))))
        writer.requestSync()
    }
}
