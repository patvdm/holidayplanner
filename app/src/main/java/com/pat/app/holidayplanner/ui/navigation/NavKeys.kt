package com.pat.app.holidayplanner.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Destinations that take over the whole screen: no bottom tab bar while they are on top. */
sealed interface FullScreenKey : NavKey

@Serializable
data object ExploreRootKey : NavKey

@Serializable
data object TripsRootKey : NavKey

@Serializable
data object RoutesRootKey : NavKey

@Serializable
data object SpotsRootKey : NavKey

/** A country and its cities. [cityId], when set, is the city to scroll to on opening. */
@Serializable
data class CountryKey(val countryId: String, val cityId: String = "") : NavKey

@Serializable
data class SpotKey(
    val spotId: String,
    /** Set when opened from a trip: the stop on that trip, whose day and booking the screen shows too. */
    val tripItemId: String = "",
) : NavKey

@Serializable
data class TripKey(val tripId: String) : NavKey

@Serializable
data class TripEditorKey(val tripId: String) : NavKey

@Serializable
data class TripItemKey(val itemId: String) : NavKey

@Serializable
data class RouteKey(val routeId: String) : NavKey

/**
 * The map, opened from the middle button of the tab bar. The ids do not filter anything; they say
 * where to point the camera, and come from whatever screen was showing when the button was tapped.
 */
@Serializable
data class MapKey(
    val countryId: String = "",
    val cityId: String = "",
    val spotId: String = "",
    val routeId: String = "",
    /** With [day], the map of one trip day: only what is planned for it. */
    val tripId: String = "",
    /** An ISO date, as a LocalDate does not serialise. */
    val day: String = "",
) : NavKey {
    companion object {
        /**
         * The map for a screen: a country, spot or route screen centres on itself, anything else
         * carries on where the map was last left.
         */
        fun forScreen(key: NavKey): MapKey = when (key) {
            is CountryKey -> MapKey(countryId = key.countryId)
            is SpotKey -> MapKey(spotId = key.spotId)
            is RouteKey -> MapKey(routeId = key.routeId)
            is MapKey -> key
            else -> MapKey()
        }
    }
}

@Serializable
data object SearchKey : FullScreenKey

@Serializable
data object SettingsKey : FullScreenKey
