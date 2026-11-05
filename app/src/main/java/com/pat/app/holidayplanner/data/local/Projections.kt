package com.pat.app.holidayplanner.data.local

import androidx.room.Embedded
import com.pat.app.holidayplanner.domain.Priority
import com.pat.app.holidayplanner.domain.SpotStatus
import com.pat.app.holidayplanner.domain.SpotType
import com.pat.app.holidayplanner.domain.TripItemType

/** A country with the counts shown on its row. */
data class CountryRow(
    @Embedded val country: CountryEntity,
    val cityCount: Int,
    val spotCount: Int,
)

/** A place with the number of spots under it. */
data class CityRow(
    @Embedded val place: CityEntity,
    val spotCount: Int,
)

/** A spot with everything its row shows. */
data class SpotRow(
    @Embedded val spot: SpotEntity,
    val heartCount: Int,
    /** Everything attached, photos included; what a delete takes with it. */
    val linkCount: Int,
    /** The links proper, with the photos left out. */
    val webLinkCount: Int,
    val heartedByMe: Boolean,
)

/** Who hearted a spot, with their name resolved. */
data class HeartRow(
    val userId: String,
    val name: String,
)

/** Where a spot sits in the hierarchy, for breadcrumbs and the Maps fallback query. */
data class SpotContext(
    val cityId: String,
    val cityName: String,
    val countryId: String,
    val countryName: String,
    val isoCode: String,
)

/** A place as offered when moving a spot somewhere else. */
data class CityOption(
    val cityId: String,
    val cityName: String,
    val countryId: String,
    val countryName: String,
)

/**
 * Somewhere a spot can live: a city, or a country itself when [cityId] is empty. Offered when
 * moving a spot, countries in their dragged order with each one's cities after it.
 */
data class SpotHome(
    val countryId: String,
    val countryName: String,
    val isoCode: String,
    val cityId: String,
    val cityName: String,
) {
    val isCountry: Boolean get() = cityId.isEmpty()
}

/** A spot as offered by the picker, with the hierarchy that identifies it. */
data class SpotPickerRow(
    val spotId: String,
    val spotName: String,
    val cityId: String,
    val cityName: String,
    val countryId: String,
    val countryName: String,
    val isoCode: String,
) {
    /** A spot of the country itself has no city to name. */
    val group: String get() = if (cityName.isBlank()) countryName else "$countryName › $cityName"
}


/** A stop on a day route, with the spot it points at: what the row shows and what Maps needs. */
data class RouteStopRow(
    val stopId: String,
    val sortKey: String,
    val onMaps: Boolean,
    val spotId: String,
    val name: String,
    val cityId: String,
    val cityName: String,
    val address: String,
    val lat: Double?,
    val lng: Double?,
    val googlePlaceId: String,
    val imageUrl: String,
    val notes: String,
    val marks: String,
    val priority: Priority,
    val status: SpotStatus,
    val type: SpotType,
)

/**
 * One search result, whatever kind of thing matched.
 *
 * [navId] is what tapping it opens, which is not always [id]: a link opens the spot it sits on,
 * and a city opens its country.
 */
data class SearchHit(
    val type: String,
    val id: String,
    val navId: String,
    val title: String,
    val subtitle: String,
)


/** A spot with a known position, as the map needs it: where, what kind, and how much it matters. */
data class SpotPin(
    val spotId: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    val status: SpotStatus,
    val priority: Priority,
    val type: SpotType,
    /** What the spot is good for, as space-separated keys; see SpotMark. */
    val marks: String,
    /** As OpeningHours writes it; blank until asked. */
    val openingHours: String,
    val cityId: String,
    val cityName: String,
    val countryId: String,
    val countryName: String,
)

/** One stop of a route as the map draws it: where it is, which spot, and which route it belongs to. */
data class RouteLinePoint(
    val routeId: String,
    val spotId: String,
    val routeName: String,
    val cityId: String,
    val createdAt: Long,
    val completed: Boolean,
    val lat: Double,
    val lng: Double,
)

/** A stop on one of a trip's routes and when it is open, for warning about a day it is shut. */
data class RouteStopHours(
    val routeId: String,
    val spotName: String,
    val openingHours: String,
)

/** A route as the Routes tab lists it. */
data class RouteRow(
    @Embedded val route: RouteEntity,
    val cityName: String,
    val countryName: String,
    val countryId: String,
    val stopCount: Int,
)

/** A trip as the Trips tab lists it, with the country it is to. */
data class TripRow(
    @Embedded val trip: TripEntity,
    val countryName: String?,
    val isoCode: String?,
)

/**
 * One thing on a trip day, with the spot or route it points at resolved: a spot row shows the
 * spot's tags and picture, a route row how far along the walk is.
 */
data class TripItemRow(
    @Embedded val item: TripItemEntity,
    val spotName: String?,
    val spotPriority: Priority?,
    val spotStatus: SpotStatus?,
    val spotType: SpotType?,
    val spotMarks: String?,
    val spotImageUrl: String?,
    val spotOpeningHours: String?,
    val routeName: String?,
    val routeCompleted: Boolean?,
    val routeCityName: String?,
    val routeStopCount: Int,
) {
    /**
     * What the row is called: the thing it points at if it still exists, else what was typed. A
     * flight is called after where it goes, since "AMS \u2192 LIS" says more than its number does.
     */
    val title: String
        get() = when (item.type) {
            TripItemType.SPOT -> spotName ?: item.title
            TripItemType.ROUTE -> routeName ?: item.title
            TripItemType.FLIGHT -> listOf(item.origin, item.destination)
                .filter { it.isNotBlank() }
                .joinToString(" \u2192 ")
                .ifBlank { item.title }
        }
}

/** A spot as the Spots tab lists it: the spot plus where it is. */
data class SpotListRow(
    @Embedded val spot: SpotEntity,
    val cityName: String,
    val countryId: String,
    val countryName: String,
    val isoCode: String,
)
