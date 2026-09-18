package com.pat.app.holidayplanner.data.repo

import com.pat.app.holidayplanner.data.local.SyncMeta
import com.pat.app.holidayplanner.data.local.TripEntity
import com.pat.app.holidayplanner.data.local.TripItemEntity
import com.pat.app.holidayplanner.data.local.dao.CascadeDao
import com.pat.app.holidayplanner.data.local.dao.TripDao
import com.pat.app.holidayplanner.data.local.dao.TripItemDao
import com.pat.app.holidayplanner.data.sync.Transactor
import com.pat.app.holidayplanner.domain.TripItemType
import com.pat.app.holidayplanner.domain.newId
import kotlinx.coroutines.flow.Flow

class TripRepository(
    private val dao: TripDao,
    private val itemDao: TripItemDao,
    private val cascadeDao: CascadeDao,
    private val transactor: Transactor,
    private val writer: LocalWriter,
) {

    fun observeTrips(): Flow<List<TripEntity>> = dao.observeAll()

    fun observeTrip(id: String): Flow<TripEntity?> = dao.observeById(id)

    fun observeItems(tripId: String): Flow<List<TripItemEntity>> = itemDao.observeByTrip(tripId)

    suspend fun add(name: String, countryId: String, startDate: String, endDate: String): String {
        val stamp = writer.stamp()
        val id = newId()
        val trip = TripEntity(
            meta = SyncMeta.created(id, stamp.now, stamp.userId),
            name = name.trim(),
            countryId = countryId,
            startDate = startDate,
            endDate = endDate,
            notes = "",
        )
        dao.upsert(listOf(trip))
        writer.requestSync()
        return id
    }

    suspend fun update(trip: TripEntity) {
        val stamp = writer.stamp()
        dao.upsert(listOf(trip.copy(meta = trip.meta.touched(stamp.now, stamp.userId))))
        writer.requestSync()
    }

    suspend fun findItem(id: String): TripItemEntity? = itemDao.find(id)

    /**
     * A new item on a day of a trip. A spot or route item points at the thing it is; a flight
     * starts empty and is filled in on its own screen.
     */
    suspend fun addItem(
        tripId: String,
        type: TripItemType,
        title: String = "",
        spotId: String = "",
        routeId: String = "",
        startLocal: String = "",
    ): String {
        val stamp = writer.stamp()
        val id = newId()
        val item = TripItemEntity(
            meta = SyncMeta.created(id, stamp.now, stamp.userId),
            tripId = tripId,
            type = type,
            title = title.trim(),
            origin = "",
            destination = "",
            startLocal = startLocal,
            startTimeZone = "",
            endLocal = "",
            endTimeZone = "",
            spotId = spotId,
            routeId = routeId,
            bookingRef = "",
            costMinor = null,
            currency = "",
            docUrl = "",
            notes = "",
        )
        itemDao.upsert(listOf(item))
        writer.requestSync()
        return id
    }

    suspend fun updateItem(item: TripItemEntity) {
        val stamp = writer.stamp()
        itemDao.upsert(listOf(item.copy(meta = item.meta.touched(stamp.now, stamp.userId))))
        writer.requestSync()
    }

    suspend fun deleteItem(id: String) {
        val current = itemDao.find(id) ?: return
        val stamp = writer.stamp()
        itemDao.upsert(listOf(current.copy(meta = current.meta.touched(stamp.now, stamp.userId, deleted = true))))
        writer.requestSync()
    }

    /**
     * The zone a new item should start with: whatever the trip already uses, else the device's.
     * Most trips happen in one place, so repeating the last answer is nearly always right.
     */
    suspend fun suggestedTimeZone(tripId: String): String = itemDao.lastTimeZone(tripId).orEmpty()

    suspend fun deleteImpact(id: String): DeleteImpact = DeleteImpact(items = dao.countItems(id))

    suspend fun delete(id: String) {
        val stamp = writer.stamp()
        transactor.transaction {
            cascadeDao.deleteItemsOfTrip(id, stamp.now, stamp.userId)
            cascadeDao.deleteTrip(id, stamp.now, stamp.userId)
        }
        writer.requestSync()
    }
}
