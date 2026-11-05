package com.pat.app.holidayplanner.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.pat.app.holidayplanner.domain.LinkSource
import com.pat.app.holidayplanner.domain.Priority
import com.pat.app.holidayplanner.domain.SpotStatus
import com.pat.app.holidayplanner.domain.SpotType
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
    /** A picture, found automatically or pasted by hand. See [com.pat.app.holidayplanner.domain.NO_PICTURE]. */
    val imageUrl: String,
    /** Where [imageUrl] came from, as [com.pat.app.holidayplanner.domain.PictureOrigin] writes it. */
    val imageSource: String = "",
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
    val imageUrl: String,
    /** Anything worth remembering about the city as a whole. */
    val notes: String,
    /** Where [imageUrl] came from, as [com.pat.app.holidayplanner.domain.PictureOrigin] writes it. */
    val imageSource: String = "",
) : SyncEntity

@Entity(
    tableName = "spots",
    primaryKeys = ["id"],
    indices = [Index("parentCityId"), Index("parentCountryId"), Index("sortKey")],
)
data class SpotEntity(
    @Embedded override val meta: SyncMeta,
    /** The city the spot is in. Empty for a spot that belongs to its country as a whole. */
    val parentCityId: String,
    /** Set only when [parentCityId] is empty: the country the spot belongs to directly. */
    val parentCountryId: String,
    val name: String,
    /** POI unless somebody said otherwise. */
    val type: SpotType,
    val googlePlaceId: String,
    val lat: Double?,
    val lng: Double?,
    val address: String,
    /** The one Google Maps link for this spot. A spot is a place, and a place is one link. */
    val mapsUrl: String,
    val notes: String,
    val status: SpotStatus,
    /** Space-separated [com.pat.app.holidayplanner.domain.SpotMark] keys; empty for most spots. */
    val marks: String,
    val sortKey: String,
    val priority: Priority,
    val imageUrl: String,
    /** When it is open, as [com.pat.app.holidayplanner.domain.OpeningHours] writes it; blank until asked. */
    val openingHours: String = "",
    /** Where [imageUrl] came from, as [com.pat.app.holidayplanner.domain.PictureOrigin] writes it. */
    val imageSource: String = "",
) : SyncEntity

/**
 * A link or a photo, hanging on whatever it is about: a spot, or the city or country itself for the
 * things that belong to a place rather than to one address in it. Exactly one of [spotId], [cityId]
 * and [countryId] is set; a link with none belongs nowhere and is never written.
 */
@Entity(
    tableName = "links",
    primaryKeys = ["id"],
    indices = [Index("spotId"), Index("cityId"), Index("countryId")],
)
data class LinkEntity(
    @Embedded override val meta: SyncMeta,
    val spotId: String,
    val cityId: String,
    val countryId: String,
    val url: String,
    val source: LinkSource,
    /** No longer written: a link is shown as itself. Kept so older rows keep what they said. */
    val note: String,
) : SyncEntity

@Entity(tableName = "trips", primaryKeys = ["id"], indices = [Index("startDate"), Index("countryId")])
data class TripEntity(
    @Embedded override val meta: SyncMeta,
    val name: String,
    /** The country this trip is to. Its spots and routes are picked from there. */
    val countryId: String,
    /** yyyy-MM-dd. */
    val startDate: String,
    /** yyyy-MM-dd. */
    val endDate: String,
    val notes: String,
) : SyncEntity

/**
 * One thing on one day of a trip. A FLIGHT carries its own details; a SPOT points at [spotId] and a
 * ROUTE at [routeId], both of which have screens of their own.
 */
@Entity(
    tableName = "trip_items",
    primaryKeys = ["id"],
    indices = [Index("tripId"), Index("spotId"), Index("routeId")],
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
    val spotId: String,
    val routeId: String,
    val bookingRef: String,
    val notes: String,
) : SyncEntity

/**
 * A walk through one city's spots, planned in the app and opened in Google Maps. It stands on its
 * own; a trip may point at it from a day.
 */
@Entity(tableName = "routes", primaryKeys = ["id"], indices = [Index("cityId")])
data class RouteEntity(
    @Embedded override val meta: SyncMeta,
    val cityId: String,
    val name: String,
    /** Ticked by hand once the walk has been walked. */
    val completed: Boolean,
    val notes: String,
) : SyncEntity

@Entity(
    tableName = "route_stops",
    primaryKeys = ["id"],
    indices = [Index("routeId"), Index("spotId")],
)
data class RouteStopEntity(
    @Embedded override val meta: SyncMeta,
    val routeId: String,
    val spotId: String,
    val sortKey: String,
    /**
     * Whether this stop goes into the Google Maps route. A stop that is on the walk but not in the
     * directions — somewhere already seen, or somewhere to decide about later — stays in the list
     * and keeps its place in the order.
     */
    val onMaps: Boolean,
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
