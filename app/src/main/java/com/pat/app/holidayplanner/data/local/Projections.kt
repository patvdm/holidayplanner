package com.pat.app.holidayplanner.data.local

import androidx.room.Embedded
import com.pat.app.holidayplanner.domain.SpotStatus

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
    val linkCount: Int,
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
    val countryName: String,
)

/** A spot as offered by the picker, with the hierarchy that identifies it. */
data class SpotPickerRow(
    val spotId: String,
    val spotName: String,
    val cityName: String,
    val countryName: String,
    val isoCode: String,
) {
    val group: String get() = "$countryName › $cityName"
}

/** An inbox link with the name of whoever shared it. */
data class InboxRow(
    @Embedded val link: LinkEntity,
    val authorName: String,
)

/** A stop on a day route, with the spot it points at: what the row shows and what Maps needs. */
data class RouteStopRow(
    val stopId: String,
    val sortKey: String,
    val spotId: String,
    val name: String,
    val cityName: String,
    val address: String,
    val lat: Double?,
    val lng: Double?,
    val googlePlaceId: String,
)

/**
 * One search result, whatever kind of thing matched.
 *
 * [navId] is what tapping it opens, which is not always [id]: a link opens the spot it sits on.
 */
data class SearchHit(
    val type: String,
    val id: String,
    val navId: String,
    val title: String,
    val subtitle: String,
)

/** Something the other person added recently. [navId] is what tapping it opens. */
data class FeedRow(
    val type: String,
    val id: String,
    val navId: String,
    val title: String,
    val place: String,
    val createdAt: Long,
    val authorName: String,
)

/** A spot with a known position, as Near me and the map need it. */
data class SpotPin(
    val spotId: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    val status: SpotStatus,
    val cityName: String,
    val countryName: String,
)
