package com.pat.app.holidayplanner.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.pat.app.holidayplanner.domain.LinkSource
import com.pat.app.holidayplanner.domain.Priority
import com.pat.app.holidayplanner.domain.SpotStatus
import com.pat.app.holidayplanner.domain.SpotType
import com.pat.app.holidayplanner.domain.TravelMode
import com.pat.app.holidayplanner.domain.TripItemType

/**
 * One entity per sheet tab, with the shared columns embedded as [SyncMeta].
 *
 * There are no foreign key constraints on purpose: a pull can deliver a child row before its
 * parent. The indices are here for query speed only. Text columns use "" rather than null for an
 * absent value, matching the sheet, while numbers that have a meaningful zero are nullable.
 */

@Entity(tableName = "users", primaryKeys = ["id"])
data class UserEntity(
    @Embedded override val meta: SyncMeta,
    val name: String,
) : SyncEntity

@Entity(tableName = "countries", primaryKeys = ["id"], indices = [Index("sortKey")])
data class CountryEntity(
    @Embedded override val meta: SyncMeta,
    val name: String,
    val isoCode: String,
    val sortKey: String,
    val priority: Priority,
) : SyncEntity

@Entity(
    tableName = "cities",
    primaryKeys = ["id"],
    indices = [Index("countryId"), Index("sortKey")],
)
data class CityEntity(
    @Embedded override val meta: SyncMeta,
    val countryId: String,
    val name: String,
    val googlePlaceId: String,
    val lat: Double?,
    val lng: Double?,
    val sortKey: String,
    val priority: Priority,
) : SyncEntity

@Entity(
    tableName = "spots",
    primaryKeys = ["id"],
    indices = [Index("parentCityId"), Index("sortKey")],
)
data class SpotEntity(
    @Embedded override val meta: SyncMeta,
    val parentCityId: String,
    val name: String,
    /** Null when nobody has said what kind of place this is. */
    val type: SpotType?,
    val googlePlaceId: String,
    val lat: Double?,
    val lng: Double?,
    val address: String,
    /** The one Google Maps link for this spot. A spot is a place, and a place is one link. */
    val mapsUrl: String,
    val notes: String,
    val tags: List<String>,
    val status: SpotStatus,
    val sortKey: String,
    val priority: Priority,
) : SyncEntity

@Entity(tableName = "links", primaryKeys = ["id"], indices = [Index("spotId")])
data class LinkEntity(
    @Embedded override val meta: SyncMeta,
    /** The spot this link is about. A link never exists without one. */
    val spotId: String,
    val url: String,
    val source: LinkSource,
    val note: String,
) : SyncEntity

@Entity(tableName = "hearts", primaryKeys = ["id"], indices = [Index("spotId"), Index("userId")])
data class HeartEntity(
    @Embedded override val meta: SyncMeta,
    val spotId: String,
    val userId: String,
) : SyncEntity

@Entity(tableName = "trips", primaryKeys = ["id"], indices = [Index("startDate")])
data class TripEntity(
    @Embedded override val meta: SyncMeta,
    val name: String,
    /** The city this trip is to. Everything on the trip is picked from its spots. */
    val cityId: String,
    /** yyyy-MM-dd. */
    val startDate: String,
    /** yyyy-MM-dd. */
    val endDate: String,
    val countryIds: List<String>,
    val notes: String,
) : SyncEntity

@Entity(
    tableName = "trip_items",
    primaryKeys = ["id"],
    indices = [Index("tripId"), Index("spotId")],
)
data class TripItemEntity(
    @Embedded override val meta: SyncMeta,
    val tripId: String,
    val type: TripItemType,
    val title: String,
    val origin: String,
    val destination: String,
    /** Local time, yyyy-MM-ddTHH:mm, read together with [startTimeZone]. Never converted to UTC. */
    val startLocal: String,
    val startTimeZone: String,
    val endLocal: String,
    val endTimeZone: String,
    val address: String,
    val googlePlaceId: String,
    val lat: Double?,
    val lng: Double?,
    val spotId: String,
    /** Only a DAY_ROUTE uses this: the one city its walk stays inside. */
    val cityId: String,
    val travelMode: TravelMode?,
    val bookingRef: String,
    /** Integer minor units, paired with [currency]. Never a float. */
    val costMinor: Long?,
    val currency: String,
    val docUrl: String,
    val notes: String,
) : SyncEntity

@Entity(
    tableName = "route_stops",
    primaryKeys = ["id"],
    indices = [Index("tripItemId"), Index("spotId")],
)
data class RouteStopEntity(
    @Embedded override val meta: SyncMeta,
    val tripItemId: String,
    val spotId: String,
    val sortKey: String,
) : SyncEntity

/**
 * The server clock of the last successful pull. It lives in the database rather than DataStore so
 * that clearing the database also clears it: otherwise a recreated database would never ask for the
 * rows it just lost.
 */
@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val lastServerTime: Long,
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}
