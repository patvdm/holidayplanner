package com.pat.app.holidayplanner.data.repo

import com.pat.app.holidayplanner.data.local.CountryEntity
import com.pat.app.holidayplanner.data.local.CountryRow
import com.pat.app.holidayplanner.data.local.SyncMeta
import com.pat.app.holidayplanner.data.local.dao.CascadeDao
import com.pat.app.holidayplanner.data.local.dao.CountryDao
import com.pat.app.holidayplanner.data.sync.Transactor
import com.pat.app.holidayplanner.domain.Priority
import com.pat.app.holidayplanner.domain.SortKey
import com.pat.app.holidayplanner.domain.newId
import kotlinx.coroutines.flow.Flow

class CountryRepository(
    private val dao: CountryDao,
    private val cascadeDao: CascadeDao,
    private val transactor: Transactor,
    private val writer: LocalWriter,
) {

    fun observeCountries(): Flow<List<CountryEntity>> = dao.observeAll()

    /** Countries with their place and spot counts, as the list screen shows them. */
    fun observeRows(): Flow<List<CountryRow>> = dao.observeRows()

    fun observeCountry(id: String): Flow<CountryEntity?> = dao.observeById(id)

    suspend fun add(name: String, isoCode: String): String {
        val stamp = writer.stamp()
        val id = newId()
        val country = CountryEntity(
            meta = SyncMeta.created(id, stamp.now, stamp.userId),
            name = name.trim(),
            isoCode = isoCode.trim().uppercase(),
            sortKey = SortKey.before(dao.firstSortKey(Priority.NORMAL)),
            priority = Priority.NORMAL,
            imageUrl = "",
        )
        dao.upsert(listOf(country))
        writer.requestSync()
        return id
    }

    /** The country with this ISO code, if it has been added already. */
    suspend fun findByIsoCode(isoCode: String): CountryEntity? =
        dao.findByIsoCode(isoCode.trim().uppercase())

    suspend fun rename(id: String, name: String) = edit(id) { it.copy(name = name.trim()) }

    /** The country changes group, and lands at the top of the one it joins. */
    suspend fun setPriority(id: String, priority: Priority) = edit(id) {
        if (it.priority == priority) {
            it
        } else {
            it.copy(priority = priority, sortKey = SortKey.before(dao.firstSortKey(priority, id)))
        }
    }

    suspend fun togglePriority(id: String) {
        val current = dao.find(id) ?: return
        setPriority(id, if (current.priority == Priority.HIGH) Priority.NORMAL else Priority.HIGH)
    }

    /** Blank asks for a picture to be found again; anything else is kept as given. */
    suspend fun setImageUrl(id: String, url: String) = edit(id) { it.copy(imageUrl = url.trim()) }

    fun observeWithoutPicture(): Flow<List<CountryEntity>> = dao.observeWithoutPicture()

    /** Reorders by rewriting one row: [previousKey] and [nextKey] are the new neighbours' keys. */
    suspend fun move(id: String, previousKey: String?, nextKey: String?) =
        edit(id) { it.copy(sortKey = SortKey.between(previousKey, nextKey)) }

    suspend fun deleteImpact(id: String): DeleteImpact =
        DeleteImpact(cities = dao.countCities(id), spots = dao.countSpots(id))

    /** Soft deletes the country and everything under it, in one transaction. */
    suspend fun delete(id: String) {
        val stamp = writer.stamp()
        transactor.transaction {
            cascadeDao.deleteStopsOfCountry(id, stamp.now, stamp.userId)
            cascadeDao.deleteStopsOfCountryRoutes(id, stamp.now, stamp.userId)
            cascadeDao.deleteRoutesOfCountry(id, stamp.now, stamp.userId)
            cascadeDao.deleteLinksOfCountry(id, stamp.now, stamp.userId)
            cascadeDao.deleteSpotsOfCountry(id, stamp.now, stamp.userId)
            cascadeDao.deleteCitiesOfCountry(id, stamp.now, stamp.userId)
            cascadeDao.deleteCountry(id, stamp.now, stamp.userId)
        }
        writer.requestSync()
    }

    private suspend fun edit(id: String, change: suspend (CountryEntity) -> CountryEntity) {
        val current = dao.find(id) ?: return
        val stamp = writer.stamp()
        val updated = change(current)
        dao.upsert(listOf(updated.copy(meta = updated.meta.touched(stamp.now, stamp.userId))))
        writer.requestSync()
    }
}
