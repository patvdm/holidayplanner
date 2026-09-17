package com.pat.app.holidayplanner.data.repo

import com.pat.app.holidayplanner.data.local.SyncMeta
import com.pat.app.holidayplanner.data.local.TripEntity
import com.pat.app.holidayplanner.data.local.TripItemEntity
import com.pat.app.holidayplanner.data.local.dao.CascadeDao
import com.pat.app.holidayplanner.data.local.dao.TripDao
import com.pat.app.holidayplanner.data.local.dao.TripItemDao
import com.pat.app.holidayplanner.data.sync.Transactor
import com.pat.app.holidayplanner.domain.LocalTimes
import com.pat.app.holidayplanner.domain.TravelMode
import com.pat.app.holidayplanner.domain.TripItemType
import com.pat.app.holidayplanner.domain.newId
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/** A day starts in the morning, which is when someone planning one is likely to set off. */
private const val DAY_ROUTE_START_HOUR = 9

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

    suspend fun add(
        name: String,
        cityId: String,
        countryIds: List<String>,
        startDate: String = "",
        endDate: String = "",
    ): String {
        val stamp = writer.stamp()
        val id = newId()
        val trip = TripEntity(
            meta = SyncMeta.created(id, stamp.now, stamp.userId),
            name = name.trim(),
            cityId = cityId,
            startDate = startDate,
            endDate = endDate,
            countryIds = countryIds,
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
     * A new item on a trip. Everything beyond the type is optional, because a flight number often
     * arrives before the times do, and a half-filled item is more useful than none.
     */
    suspend fun addItem(
        tripId: String,
        type: TripItemType,
        title: String = "",
        spotId: String = "",
        cityId: String = "",
        address: String = "",
        lat: Double? = null,
        lng: Double? = null,
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
            startLocal = "",
            startTimeZone = "",
            endLocal = "",
            endTimeZone = "",
            address = address,
            googlePlaceId = "",
            lat = lat,
            lng = lng,
            spotId = spotId,
            cityId = cityId,
            travelMode = null,
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

    /**
     * A day route dated and ready for stops, as "Plan a day" creates it. Written as two updates to
     * the same row rather than a wider [addItem], so the row still syncs once.
     */
    suspend fun addDayRoute(tripId: String, title: String, cityId: String, date: LocalDate?): String {
        val id = addItem(tripId = tripId, type = TripItemType.DAY_ROUTE, title = title, cityId = cityId)
        val item = itemDao.find(id) ?: return id
        val zone = suggestedTimeZone(tripId).ifBlank { LocalTimes.deviceZone() }
        updateItem(
            item.copy(
                startLocal = date?.let { LocalTimes.write(it.atTime(DAY_ROUTE_START_HOUR, 0)) }.orEmpty(),
                startTimeZone = zone,
                endTimeZone = zone,
                travelMode = TravelMode.WALKING,
            ),
        )
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
        transactor.transaction {
            cascadeDao.deleteStopsOfItem(id, stamp.now, stamp.userId)
            itemDao.upsert(
                listOf(current.copy(meta = current.meta.touched(stamp.now, stamp.userId, deleted = true))),
            )
        }
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
            cascadeDao.deleteStopsOfTrip(id, stamp.now, stamp.userId)
            cascadeDao.deleteItemsOfTrip(id, stamp.now, stamp.userId)
            cascadeDao.deleteTrip(id, stamp.now, stamp.userId)
        }
        writer.requestSync()
    }
}
