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
data object SpotsRootKey : NavKey

@Serializable
data class CountryKey(val countryId: String) : NavKey

@Serializable
data class CityKey(val cityId: String) : NavKey

@Serializable
data class SpotKey(val spotId: String) : NavKey

@Serializable
data class TripKey(val tripId: String) : NavKey

@Serializable
data class TripEditorKey(val tripId: String) : NavKey

@Serializable
data class TripItemKey(val itemId: String) : NavKey

@Serializable
data class RouteKey(val routeId: String) : NavKey

/** A full screen map. The ids do not filter anything; they say where to point the camera. */
@Serializable
data class MapKey(
    val countryId: String = "",
    val cityId: String = "",
    val spotId: String = "",
) : FullScreenKey

@Serializable
data object SearchKey : FullScreenKey

@Serializable
data object SettingsKey : FullScreenKey
